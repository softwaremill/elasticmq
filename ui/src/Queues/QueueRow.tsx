import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import IconButton from "@material-ui/core/IconButton";
import {KeyboardArrowDown, KeyboardArrowRight} from "@material-ui/icons";
import React, {useState} from "react";
import {QueueMessagesData} from "./QueueMessageData";
import RowDetails from "./QueueRowDetails";

function QueueTableRow(props: { row: QueueMessagesData }) {

    const [isExpanded, setIsExpanded] = useState<boolean>(false);

    function ExpandableArrowButton(props: { isExpanded: boolean }) {
        return <IconButton aria-label="open-details" size="small"
                           onClick={() => setIsExpanded(prevState => !prevState)}>
            {props.isExpanded ? <KeyboardArrowRight/> : <KeyboardArrowDown/>}
        </IconButton>
    }

    const {row} = props
    return (
        <>
            <TableRow key={row.queueName} className={`queue-row`}>
                <TableCell>
                    <ExpandableArrowButton isExpanded={isExpanded}/>
                </TableCell>
                <TableCell component="th" scope="row">{row.queueName}</TableCell>
                <TableCell align="right">{row.currentMessagesNumber}</TableCell>
                <TableCell align="right">{row.delayedMessagesNumber}</TableCell>
                <TableCell align="right">{row.notVisibleMessagesNumber}</TableCell>
            </TableRow>
            <RowDetails props={{isExpanded: isExpanded, queueName: row.queueName}}/>
        </>
    )
}

export default QueueTableRow