import React from "react";
import TableContainer from "@material-ui/core/TableContainer";
import Paper from "@material-ui/core/Paper";
import Table from "@material-ui/core/Table";
import TableHead from "@material-ui/core/TableHead";
import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import {TableBody} from "@material-ui/core";
import "../styles/queue.css";
import Row from "./QueueRow";
import RefreshQueuesData from "./RefreshQueuesData";
import useMountEffect from "./UseMountEffect";

const QueuesTable: React.FC = () => {
    const queuesOverallData = RefreshQueuesData.useRefreshQueueData()
    useMountEffect(() => {
        const fetchInitialStatistics = async () => {
            const initialStatistics = await queuesOverallData.obtainInitialStatistics()
            queuesOverallData.setQueuesOverallData((prevState) => {
                if (prevState.length === 0) {
                    return initialStatistics
                } else {
                    return prevState;
                }
            })
        }
        fetchInitialStatistics();
    });

    return (
        <TableContainer component={Paper} elevation={2}>
            <Table size="small" aria-label="a dense table">
                <TableHead>
                    <TableRow key="Queue table header">
                        <TableCell/>
                        <TableCell>Name</TableCell>
                        <TableCell align="right">Approximate number of messages</TableCell>
                        <TableCell align="right">Approximate number of delayed messages</TableCell>
                        <TableCell align="right">Approximate number of not visible Messages</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {queuesOverallData.queuesOverallData.map((row) => (
                        <Row key={row.queueName} row={row}/>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    )
}

export default QueuesTable;