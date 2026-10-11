import React from "react";
import { createRoot } from "react-dom/client";
import App from "./app/App";
import "./app/globals.css";
import "./app/site-chrome.css";
import "./app/workspace.css";
import { AuthProvider } from "./features/auth/AuthProvider";
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>,
);
