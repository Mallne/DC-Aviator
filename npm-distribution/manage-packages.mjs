import { cp, mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const output = path.join(root, 'build/packages');
const packages = JSON.parse(await readFile(path.join(output, 'packages.json'), 'utf8'));
const npmCli = process.env.npm_execpath;
if (!npmCli) throw new Error('Run through npm run pack, npm run verify:fetch, or npm run publish:shared');
const npm = (args, cwd = root) => execFileSync(process.execPath, [npmCli, ...args], { cwd, stdio: 'inherit' });
const action = process.argv[2];
if (!['pack', 'verify', 'publish'].includes(action)) throw new Error(`Unknown action ${action}`);

if (action === 'publish') {
  // Publish dependency-first. Exact versions make a release a coherent linker ABI.
  const pending = new Map(packages.map(pkg => [pkg.name, pkg]));
  while (pending.size) {
    const ready = [...pending.values()].filter(pkg => Object.keys(pkg.dependencies).every(name => !pending.has(name)));
    if (!ready.length) throw new Error('Cyclic generated npm dependencies');
    for (const pkg of ready) {
      npm(['publish', path.join(output, pkg.directory), ...process.argv.slice(3)]);
      pending.delete(pkg.name);
    }
  }
} else {
  const tarballs = path.join(root, 'build/tarballs');
  await mkdir(tarballs, { recursive: true });
  const dependencies = {};
  for (const pkg of packages) {
    const directory = path.join(output, pkg.directory);
    const manifest = JSON.parse(await readFile(path.join(directory, 'package.json'), 'utf8'));
    npm(['pack', directory, '--pack-destination', tarballs, '--silent']);
    dependencies[pkg.name] = `file:${path.join(tarballs, `${pkg.name.slice(1).replace('/', '-')}-${manifest.version}.tgz`).replaceAll('\\', '/')}`;
  }
  if (action === 'verify') {
    // Keep npm away from the monorepo's pnpm-managed node_modules and lock files.
    const verification = path.join(root, 'build/verification');
    await mkdir(verification, { recursive: true });
    // Never reuse installed artifacts from a previous build of the same version.
    const fixture = await mkdtemp(path.join(verification, 'run-'));
    const ownPackage = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
    await writeFile(path.join(fixture, 'package.json'), JSON.stringify({
      name: 'aviator-npm-verification',
      private: true,
      type: 'module',
      scripts: { 'check:types': 'tsc --noEmit', test: 'node --test test/*.test.mjs' },
      devDependencies: { ...ownPackage.devDependencies, ...dependencies },
    }, null, 2));
    await cp(path.join(root, 'test'), path.join(fixture, 'test'), { recursive: true });
    await writeFile(path.join(fixture, 'tsconfig.json'), JSON.stringify({
      compilerOptions: { target: 'ES2022', module: 'NodeNext', strict: true, noEmit: true, skipLibCheck: true },
      include: ['test/**/*.ts'],
    }, null, 2));
    npm(['install', '--package-lock=false', '--ignore-scripts', '--no-audit', '--no-fund'], fixture);
    npm(['run', 'check:types'], fixture);
    npm(['test'], fixture);
  }
}
