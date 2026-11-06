import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // Caminhos relativos permitem hospedar o build em qualquer subdiretório
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    sourcemap: mode !== 'production',
    chunkSizeWarningLimit: 900
  }
}));
