import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createFetchAviatorJsClient } from '@dicentra/aviator-client-fetch';
import { AviatorJsonAdapter } from '@dicentra/aviator-plugin-adapter-json';
import { AviatorWeaverPlugin } from '@dicentra/aviator-plugin-weaver';

test('Fetch and optional plugins share Core without pulling plugins into Fetch', async () => {
  const manifest = async name => JSON.parse(await readFile(
    path.join(path.dirname(fileURLToPath(import.meta.resolve(name))), 'package.json'), 'utf8',
  ));
  const fetch = await manifest('@dicentra/aviator-client-fetch');
  for (const name of ['@dicentra/aviator-plugin-adapter-json', '@dicentra/aviator-plugin-weaver']) {
    const plugin = await manifest(name);
    assert.equal(plugin.dependencies['@dicentra/aviator-core'], fetch.dependencies['@dicentra/aviator-core']);
  }
  const visited = new Set();
  const visit = async name => {
    if (visited.has(name)) return;
    visited.add(name);
    const pkg = await manifest(name);
    for (const dependency of Object.keys(pkg.dependencies)) {
      if (dependency.startsWith('@dicentra/') && dependency !== '@dicentra/aviator-js-types') await visit(dependency);
    }
  };
  await visit('@dicentra/aviator-client-fetch');
  assert.ok(visited.has('@dicentra/aviator-core'));
  assert.deepEqual([...visited].filter(name => name.startsWith('@dicentra/aviator-plugin-')), []);
});

test('Fetch executes with and without separately packaged plugins', async () => {
  const requests = [];
  const fetcher = async (input, init) => {
    requests.push({ url: new URL(String(input)), method: String(init.method) });
    return new Response('<FeatureCollection/>', { status: 200, headers: { 'content-type': 'application/xml' } });
  };
  const spec = {
    openapi: '3.1.0',
    info: { title: 'Aviator Fetch contract', version: '1.0.0' },
    servers: [{ url: 'https://provider.test' }],
    paths: {
      '/parcels': {
        get: {
          operationId: 'findParcels',
          parameters: [{ name: 'q', in: 'query', required: true, schema: { type: 'string' } }],
          responses: { '200': { description: 'Feature response' } },
          'x-dicentra-aviator-serviceDelegateCall': 'parcel-provider',
        },
      },
    },
    'x-dicentra-aviator': '1.0.0',
  };
  const client = createFetchAviatorJsClient(JSON.stringify(spec), fetcher);
  assert.ok(client.operationIds.includes('findParcels'));
  assert.deepEqual(client.pluginIdentities, []);
  const pluginClient = createFetchAviatorJsClient(JSON.stringify(spec), fetcher, scope => {
    scope.install(AviatorJsonAdapter);
    scope.install(AviatorWeaverPlugin);
  });
  assert.deepEqual([...pluginClient.pluginIdentities].sort(), ['DC-WVxAV', 'adapter:JSON'].sort());
  const expected = { status: 200, contentType: 'application/xml', body: '<FeatureCollection/>' };
  for (const [index, activeClient] of [client, pluginClient].entries()) {
    assert.deepEqual(JSON.parse(await activeClient.request('findParcels', JSON.stringify({ q: 'A 1' }))), expected);
    assert.equal(requests[index].method, 'GET');
    assert.equal(requests[index].url.origin, 'https://provider.test');
    assert.equal(requests[index].url.pathname, '/parcels');
    assert.equal(requests[index].url.searchParams.get('q'), 'A 1');
  }
  assert.equal(requests.length, 2);
});
