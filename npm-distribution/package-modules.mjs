import { mkdir, readFile, readdir, writeFile, copyFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const input = path.join(root, 'build/compileSync/js/main/productionLibrary/kotlin');
const output = path.join(root, 'build/packages');
const version = process.argv[2];
if (!version || version === 'unspecified') throw new Error('A distribution version is required');

const publicModules = {
  'Aviator-core': { name: 'aviator-core', types: ['AviatorPlugin', 'AviatorPluginActivationScope', 'AviatorPluginConfigScope', 'AviatorPluginInstance'] },
  'Aviator-koas': { name: 'aviator-koas' },
  'Aviator-clients-fetch': { name: 'aviator-client-fetch', types: ['FetchAviatorJsClient', 'createFetchAviatorJsClient'] },
  ...Object.fromEntries([
    ['adapter-json', 'AviatorJsonAdapter'], ['adapter-xml', 'AviatorXmlAdapter'],
    ['http-auth', 'AviatorHttpAuthPlugin'], ['interception', 'AviatorInterceptionPlugin'],
    ['otel', 'AviatorOpenTelemetryPlugin'], ['synapse', 'AviatorSynapsePlugin'],
    ['weaver', 'AviatorWeaverPlugin'],
  ].map(([id, singleton]) => [`Aviator-plugins-${id}`, { name: `aviator-plugin-${id}`, singleton }])),
};
const files = (await readdir(input)).filter(file => file.endsWith('.mjs') && file !== 'Aviator-npm-distribution.mjs');
const packageName = file => `@dicentra/${publicModules[path.basename(file, '.mjs')]?.name ?? `aviator-js-${path.basename(file, '.mjs').toLowerCase().replaceAll('_', '-')}`}`;
const names = new Map(files.map(file => [file, packageName(file)]));
if (new Set(names.values()).size !== names.size) throw new Error('Generated npm package names collide');
for (const module of Object.keys(publicModules)) {
  if (!names.has(`${module}.mjs`)) throw new Error(`Missing public module ${module}`);
}
const npm = JSON.parse(await readFile(path.join(root, '../build/js/packages/Aviator-npm-distribution/package.json'), 'utf8'));
const manifests = [];

async function emit(name, manifest, contents) {
  const directory = path.join(output, name.split('/')[1]);
  await mkdir(directory, { recursive: true });
  await writeFile(path.join(directory, 'package.json'), `${JSON.stringify({ name, version, type: 'module', ...manifest }, null, 2)}\n`);
  for (const [file, content] of Object.entries(contents)) await writeFile(path.join(directory, file), content);
  manifests.push({ name, directory: path.relative(output, directory), dependencies: manifest.dependencies ?? {} });
  return directory;
}

const typesName = '@dicentra/aviator-js-types';
await emit(typesName, { types: './index.d.mts', exports: { '.': { types: './index.d.mts' } } }, {
  'index.d.mts': await readFile(path.join(input, 'Aviator-npm-distribution.d.mts'), 'utf8'),
});

for (const file of files) {
  const info = publicModules[path.basename(file, '.mjs')];
  const dependencies = {};
  // Only rewrite module specifiers, never arbitrary strings in generated code.
  const source = (await readFile(path.join(input, file), 'utf8')).replace(
    /(\bfrom\s*|\bimport\s*)(['"])([^'"\n]+)\2/g,
    (match, prefix, quote, specifier) => {
      if (specifier.startsWith('./')) {
        const dependency = names.get(specifier.slice(2));
        if (!dependency) throw new Error(`Unknown generated dependency ${specifier} in ${file}`);
        dependencies[dependency] = version;
        return `${prefix}${quote}${dependency}${quote}`;
      }
      const dependency = specifier.startsWith('@') ? specifier.split('/').slice(0, 2).join('/') : specifier.split('/')[0];
      if (!npm.dependencies[dependency]) throw new Error(`Unknown npm dependency ${specifier} in ${file}`);
      dependencies[dependency] = npm.dependencies[dependency];
      return match;
    },
  );
  let facade = `export * from './${file}';\n`;
  let declarations;
  if (info?.singleton) {
    // Kotlin ES modules expose exported properties through getters; hide that detail.
    facade += `import { ${info.singleton} as singleton } from './${file}';\nexport const ${info.singleton} = singleton.get();\n`;
    declarations = `export declare const ${info.singleton}: ReturnType<typeof import('${typesName}').${info.singleton}.get>;\n`;
  } else if (info?.types) {
    declarations = `export { ${info.types.join(', ')} } from '${typesName}';\n`;
  }
  if (declarations) dependencies[typesName] = version;
  const directory = await emit(names.get(file), {
    main: './index.mjs',
    ...(declarations ? { types: './index.d.mts' } : {}),
    exports: { '.': { ...(declarations ? { types: './index.d.mts' } : {}), import: './index.mjs' } },
    dependencies,
  }, { [file]: source, 'index.mjs': facade, ...(declarations ? { 'index.d.mts': declarations } : {}) });
  await copyFile(path.join(input, `${file}.map`), path.join(directory, `${file}.map`));
}
await writeFile(path.join(output, 'packages.json'), `${JSON.stringify(manifests, null, 2)}\n`);
console.log(`Assembled ${manifests.length} shared npm packages at ${output}`);
