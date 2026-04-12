import baseConfig from './vite.config';
import { defineConfig, mergeConfig } from 'vite';

export default mergeConfig(
  baseConfig,
  defineConfig({
    test: {
      include: ['.docker-tests/**/*.{spec,test}.ts'],
    },
  }),
);
