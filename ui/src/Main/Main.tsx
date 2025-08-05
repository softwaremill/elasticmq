import React from "react";
import NavBar from "../NavBar/NavBar";
import QueuesTable from "../Queues/QueuesTable";

const Main: React.FC = () => {
  return (
    <>
      <NavBar />
      <QueuesTable />
    </>
  );
};

export default Main;
