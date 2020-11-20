import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import IconButton from "@material-ui/core/IconButton";
import {KeyboardArrowDown, KeyboardArrowRight} from "@material-ui/icons";
import React, {useState} from "react";
import {QueueMessagesData} from "./QueueMessageData";
import RowDetails from "./QueueRowDetails";

function Row(props: { row: QueueMessagesData }) {

    const [isOpened, setIsOpened] = useState<boolean>(false);

    function ExpandableArrowButton(props: { isOpened: boolean }) {
        return <IconButton aria-label="open-details" size="small"
                           onClick={() => setIsOpened(prevState => !prevState)}>
            {props.isOpened ? <KeyboardArrowRight/> : <KeyboardArrowDown/>}
        </IconButton>
    }

    const {row} = props
    return (
        <>
            <TableRow key={row.queueName} className={`queue-row`}>
                <TableCell>
                    <ExpandableArrowButton isOpened={isOpened}/>
                </TableCell>
                <TableCell component="th" scope="row">{row.queueName}</TableCell>
                <TableCell align="right">{row.currentMessagesNumber}</TableCell>
                <TableCell align="right">{row.delayedMessagesNumber}</TableCell>
                <TableCell align="right">{row.notVisibleMessagesNumber}</TableCell>
            </TableRow>
            <RowDetails props={{isOpen: isOpened, queueName: row.queueName}}/>
        </>
    )
}

export default Row