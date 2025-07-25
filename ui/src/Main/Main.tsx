import React from "react";
import NavBar from "../NavBar/NavBar";
import QueuesTable from "../Queues/QueuesTable";
import { SnackbarProvider } from "notistack";

const Main: React.FC = () => {
  return (
    <SnackbarProvider maxSnack={3}>
      <NavBar />
      <QueuesTable />
    </SnackbarProvider>
  );
};

export default Main;
