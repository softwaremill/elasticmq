import React from "react";
import axios from "axios";
import {act, fireEvent, render, screen} from "@testing-library/react";
import QueueTableRow from "./QueueRow";
import {TableBody} from "@material-ui/core";
import Table from "@material-ui/core/Table";

jest.mock("axios");

beforeEach(() => {
    jest.clearAllMocks();
})

describe("<QueueRow />", () => {
    const queue1 = {
        queueName: "queueName1",
        currentMessagesNumber: 1,
        delayedMessagesNumber: 2,
        notVisibleMessagesNumber: 3,
        isOpened: false
    }

    test("renders cell values", () => {
        render(
            <Table>
                <TableBody>
                    <QueueTableRow row={queue1}/>
                </TableBody>
            </Table>
        )

        expect(screen.queryByText("queueName1")).toBeInTheDocument();
        expect(screen.queryByText("1")).toBeInTheDocument();
        expect(screen.queryByText("2")).toBeInTheDocument();
        expect(screen.queryByText("3")).toBeInTheDocument();
        expect(screen.queryByRole("button")).toBeInTheDocument()
    });

    test("clicking button should expand queue attributes section", async () => {
        const data = {
            name: "queueName1",
            attributes: {
                attribute1: "value1",
                attribute2: "value2"
            }
        };
        (axios.get as jest.Mock).mockResolvedValueOnce({data, status: 200})

        render(
            <Table>
                <TableBody>
                    <QueueTableRow row={queue1}/>
                </TableBody>
            </Table>
        )

        expect(screen.queryByText("Queue attributes")).not.toBeInTheDocument()

        await act(async () => {
            fireEvent.click(await screen.findByRole("button"))
        })

        expect(screen.queryByText("Queue attributes")).toBeInTheDocument()
        expect(screen.queryByText("attribute1"))
        expect(screen.queryByText("value1"))
        expect(screen.queryByText("attribute2"))
        expect(screen.queryByText("value1"))
    });
});