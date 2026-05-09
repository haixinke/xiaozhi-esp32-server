import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 8006,
    open: true,
  },
  build: {
    outDir: 'dist',
  },
});
