import React from "react";
import { render } from "@testing-library/react";
import App from "./App";

test("should render", () => {
  const { getByText } = render(<App />);
  const header = getByText("ElasticMQ");
  expect(header).toBeInTheDocument();
});
