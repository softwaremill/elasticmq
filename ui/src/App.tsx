import React from "react";
import Main from "./Main/Main";
import { SnackbarProvider } from "./context/SnackbarContext";

function App() {
  return (
    <SnackbarProvider>
      <Main />
    </SnackbarProvider>
  );
}

export default App;
