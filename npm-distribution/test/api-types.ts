import { createFetchAviatorJsClient } from '@dicentra/aviator-client-fetch';
import { AviatorJsonAdapter } from '@dicentra/aviator-plugin-adapter-json';
import { AviatorWeaverPlugin } from '@dicentra/aviator-plugin-weaver';
import type { AviatorPluginActivationScope } from '@dicentra/aviator-core';

// Compile-only check that independently imported declarations share the same types.
export const client = (openApiJson: string) => createFetchAviatorJsClient(openApiJson, globalThis.fetch,
  (scope: AviatorPluginActivationScope) => {
    scope.install(AviatorJsonAdapter);
    scope.install(AviatorWeaverPlugin);
  });
