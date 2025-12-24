import React from "react";
import { render } from "@testing-library/react";
import { SnackbarProvider } from "../context/SnackbarContext";

export const renderWithSnackbarProvider = (component: React.ReactElement) => {
  return render(<SnackbarProvider>{component}</SnackbarProvider>);
};
