import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import QueuesTable from "./QueuesTable";
import axios from "axios";
import "@testing-library/jest-dom";
import { SnackbarProvider } from "../context/SnackbarContext";

jest.mock("axios");
const initialData = {
  data: [
    {
      name: "queueName1",
      statistics: {
        approximateNumberOfVisibleMessages: 5,
        approximateNumberOfMessagesDelayed: 8,
        approximateNumberOfInvisibleMessages: 10,
      },
    },
    {
      name: "queueName2",
      statistics: {
        approximateNumberOfVisibleMessages: 1,
        approximateNumberOfMessagesDelayed: 3,
        approximateNumberOfInvisibleMessages: 7,
      },
    },
  ],
};

const renderWithSnackbarProvider = (component: React.ReactElement) => {
  return render(<SnackbarProvider>{component}</SnackbarProvider>);
};

beforeEach(() => {
  jest.useFakeTimers();
});

afterEach(() => {
  jest.clearAllMocks();
  jest.clearAllTimers();
});

describe("<QueuesTable />", () => {
  test("Basic information about queues should be retrieved for first time without waiting for interval", async () => {
    (axios.get as jest.Mock).mockResolvedValueOnce(initialData);

    renderWithSnackbarProvider(<QueuesTable />);

    await waitFor(() => screen.findByText("queueName1"));

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("5")).toBeInTheDocument();
    expect(await screen.findByText("8")).toBeInTheDocument();
    expect(await screen.findByText("10")).toBeInTheDocument();

    expect(await screen.findByText("queueName2")).toBeInTheDocument();
    expect(await screen.findByText("1")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();
    expect(await screen.findByText("7")).toBeInTheDocument();
  });

  test("Each second statistics for queue should be updated if there were updates on Backend side", async () => {
    const initialData = createResponseDataForQueue("queueName1", 1, 2, 3);
    const firstUpdate = createResponseDataForQueue("queueName1", 4, 5, 6);
    const secondUpdate = createResponseDataForQueue("queueName1", 7, 8, 9);

    (axios.get as jest.Mock)
      .mockResolvedValueOnce(initialData)
      .mockResolvedValueOnce(firstUpdate)
      .mockResolvedValue(secondUpdate);

    renderWithSnackbarProvider(<QueuesTable />);

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("1")).toBeInTheDocument();
    expect(await screen.findByText("2")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("4")).toBeInTheDocument();
    expect(await screen.findByText("5")).toBeInTheDocument();
    expect(await screen.findByText("6")).toBeInTheDocument();
    expect(screen.queryByText("1")).not.toBeInTheDocument();
    expect(screen.queryByText("2")).not.toBeInTheDocument();
    expect(screen.queryByText("3")).not.toBeInTheDocument();

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("7")).toBeInTheDocument();
    expect(await screen.findByText("8")).toBeInTheDocument();
    expect(await screen.findByText("9")).toBeInTheDocument();
    expect(screen.queryByText("4")).not.toBeInTheDocument();
    expect(screen.queryByText("5")).not.toBeInTheDocument();
    expect(screen.queryByText("6")).not.toBeInTheDocument();
  });

  test("Statistics should not change if retrieved data has not been changed", async () => {
    const initialData = createResponseDataForQueue("queueName1", 1, 2, 3);
    (axios.get as jest.Mock).mockResolvedValue(initialData);

    renderWithSnackbarProvider(<QueuesTable />);

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("1")).toBeInTheDocument();
    expect(await screen.findByText("2")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(await screen.findByText("queueName1")).toBeInTheDocument();
    expect(await screen.findByText("1")).toBeInTheDocument();
    expect(await screen.findByText("2")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();
  });
});

function createResponseDataForQueue(
  queueName: string,
  numberOfVisibleMessages: number,
  numberOfDelayedMessages: number,
  numberOfInvisibleMessages: number
) {
  return {
    data: [
      {
        name: queueName,
        statistics: {
          approximateNumberOfVisibleMessages: numberOfVisibleMessages,
          approximateNumberOfMessagesDelayed: numberOfDelayedMessages,
          approximateNumberOfInvisibleMessages: numberOfInvisibleMessages,
        },
      },
    ],
  };
}
