// vite.config.ts
import { defineConfig } from "file:///E:/%E5%9B%9B%E5%B7%9D%E5%A4%A7%E5%AD%A6/%E4%B8%93%E4%B8%9A%E8%AF%BE/%E5%A4%A7%E4%BA%8C%E4%B8%8B%E5%AE%9E%E8%AE%AD/project2/FreeWillase/frontend/node_modules/vite/dist/node/index.js";
import vue from "file:///E:/%E5%9B%9B%E5%B7%9D%E5%A4%A7%E5%AD%A6/%E4%B8%93%E4%B8%9A%E8%AF%BE/%E5%A4%A7%E4%BA%8C%E4%B8%8B%E5%AE%9E%E8%AE%AD/project2/FreeWillase/frontend/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import path from "path";
import Inspector from "file:///E:/%E5%9B%9B%E5%B7%9D%E5%A4%A7%E5%AD%A6/%E4%B8%93%E4%B8%9A%E8%AF%BE/%E5%A4%A7%E4%BA%8C%E4%B8%8B%E5%AE%9E%E8%AE%AD/project2/FreeWillase/frontend/node_modules/unplugin-vue-dev-locator/dist/vite.mjs";
var __vite_injected_original_dirname = "E:\\\u56DB\u5DDD\u5927\u5B66\\\u4E13\u4E1A\u8BFE\\\u5927\u4E8C\u4E0B\u5B9E\u8BAD\\project2\\FreeWillase\\frontend";
var vite_config_default = defineConfig({
  build: {
    sourcemap: "hidden"
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true
      },
      "/proxy/nvidia": {
        target: "https://health.api.nvidia.com",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/proxy\/nvidia/, "")
      }
    }
  },
  plugins: [
    vue(),
    Inspector()
  ],
  optimizeDeps: {
    include: [
      "molstar/lib/apps/viewer/lib",
      "molstar/lib/extensions/plugin/loaders",
      "molstar/lib/mol-util/color/names"
    ]
  },
  resolve: {
    alias: {
      "@": path.resolve(__vite_injected_original_dirname, "./src")
      // ✅ 定义 @ = src
    }
  }
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJFOlxcXFxcdTU2REJcdTVERERcdTU5MjdcdTVCNjZcXFxcXHU0RTEzXHU0RTFBXHU4QkZFXFxcXFx1NTkyN1x1NEU4Q1x1NEUwQlx1NUI5RVx1OEJBRFxcXFxwcm9qZWN0MlxcXFxGcmVlV2lsbGFzZVxcXFxmcm9udGVuZFwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiRTpcXFxcXHU1NkRCXHU1REREXHU1OTI3XHU1QjY2XFxcXFx1NEUxM1x1NEUxQVx1OEJGRVxcXFxcdTU5MjdcdTRFOENcdTRFMEJcdTVCOUVcdThCQURcXFxccHJvamVjdDJcXFxcRnJlZVdpbGxhc2VcXFxcZnJvbnRlbmRcXFxcdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL0U6LyVFNSU5QiU5QiVFNSVCNyU5RCVFNSVBNCVBNyVFNSVBRCVBNi8lRTQlQjglOTMlRTQlQjglOUElRTglQUYlQkUvJUU1JUE0JUE3JUU0JUJBJThDJUU0JUI4JThCJUU1JUFFJTlFJUU4JUFFJUFEL3Byb2plY3QyL0ZyZWVXaWxsYXNlL2Zyb250ZW5kL3ZpdGUuY29uZmlnLnRzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnIH0gZnJvbSAndml0ZSdcclxuaW1wb3J0IHZ1ZSBmcm9tICdAdml0ZWpzL3BsdWdpbi12dWUnXHJcbmltcG9ydCBwYXRoIGZyb20gJ3BhdGgnXHJcbmltcG9ydCBJbnNwZWN0b3IgZnJvbSAndW5wbHVnaW4tdnVlLWRldi1sb2NhdG9yL3ZpdGUnXHJcblxyXG4vLyBodHRwczovL3ZpdGUuZGV2L2NvbmZpZy9cclxuZXhwb3J0IGRlZmF1bHQgZGVmaW5lQ29uZmlnKHtcclxuICBidWlsZDoge1xyXG4gICAgc291cmNlbWFwOiAnaGlkZGVuJyxcclxuICB9LFxyXG4gIHNlcnZlcjoge1xyXG4gICAgcG9ydDogNTE3MyxcclxuICAgIHN0cmljdFBvcnQ6IHRydWUsXHJcbiAgICBwcm94eToge1xyXG4gICAgICAnL2FwaSc6IHtcclxuICAgICAgICB0YXJnZXQ6ICdodHRwOi8vbG9jYWxob3N0OjgwODEnLFxyXG4gICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcclxuICAgICAgfSxcclxuICAgICAgJy9wcm94eS9udmlkaWEnOiB7XHJcbiAgICAgICAgdGFyZ2V0OiAnaHR0cHM6Ly9oZWFsdGguYXBpLm52aWRpYS5jb20nLFxyXG4gICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcclxuICAgICAgICByZXdyaXRlOiAocCkgPT4gcC5yZXBsYWNlKC9eXFwvcHJveHlcXC9udmlkaWEvLCAnJyksXHJcbiAgICAgIH0sXHJcbiAgICB9LFxyXG4gIH0sXHJcbiAgcGx1Z2luczogW1xyXG4gICAgdnVlKCksXHJcbiAgICBJbnNwZWN0b3IoKSxcclxuICBdLFxyXG4gIG9wdGltaXplRGVwczoge1xyXG4gICAgaW5jbHVkZTogW1xyXG4gICAgICAnbW9sc3Rhci9saWIvYXBwcy92aWV3ZXIvbGliJyxcclxuICAgICAgJ21vbHN0YXIvbGliL2V4dGVuc2lvbnMvcGx1Z2luL2xvYWRlcnMnLFxyXG4gICAgICAnbW9sc3Rhci9saWIvbW9sLXV0aWwvY29sb3IvbmFtZXMnXHJcbiAgICBdLFxyXG4gIH0sXHJcbiAgcmVzb2x2ZToge1xyXG4gICAgYWxpYXM6IHtcclxuICAgICAgJ0AnOiBwYXRoLnJlc29sdmUoX19kaXJuYW1lLCAnLi9zcmMnKSwgLy8gXHUyNzA1IFx1NUI5QVx1NEU0OSBAID0gc3JjXHJcbiAgICB9LFxyXG4gIH0sXHJcbn0pXHJcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBNmEsU0FBUyxvQkFBb0I7QUFDMWMsT0FBTyxTQUFTO0FBQ2hCLE9BQU8sVUFBVTtBQUNqQixPQUFPLGVBQWU7QUFIdEIsSUFBTSxtQ0FBbUM7QUFNekMsSUFBTyxzQkFBUSxhQUFhO0FBQUEsRUFDMUIsT0FBTztBQUFBLElBQ0wsV0FBVztBQUFBLEVBQ2I7QUFBQSxFQUNBLFFBQVE7QUFBQSxJQUNOLE1BQU07QUFBQSxJQUNOLFlBQVk7QUFBQSxJQUNaLE9BQU87QUFBQSxNQUNMLFFBQVE7QUFBQSxRQUNOLFFBQVE7QUFBQSxRQUNSLGNBQWM7QUFBQSxNQUNoQjtBQUFBLE1BQ0EsaUJBQWlCO0FBQUEsUUFDZixRQUFRO0FBQUEsUUFDUixjQUFjO0FBQUEsUUFDZCxTQUFTLENBQUMsTUFBTSxFQUFFLFFBQVEsb0JBQW9CLEVBQUU7QUFBQSxNQUNsRDtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQUEsRUFDQSxTQUFTO0FBQUEsSUFDUCxJQUFJO0FBQUEsSUFDSixVQUFVO0FBQUEsRUFDWjtBQUFBLEVBQ0EsY0FBYztBQUFBLElBQ1osU0FBUztBQUFBLE1BQ1A7QUFBQSxNQUNBO0FBQUEsTUFDQTtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQUEsRUFDQSxTQUFTO0FBQUEsSUFDUCxPQUFPO0FBQUEsTUFDTCxLQUFLLEtBQUssUUFBUSxrQ0FBVyxPQUFPO0FBQUE7QUFBQSxJQUN0QztBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
