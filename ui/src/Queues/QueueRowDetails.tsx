import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import Collapse from "@material-ui/core/Collapse";
import Box from "@material-ui/core/Box";
import Typography from "@material-ui/core/Typography";
import Table from "@material-ui/core/Table";
import TableHead from "@material-ui/core/TableHead";
import {TableBody} from "@material-ui/core";
import React, {useState} from "react";
import QueueService from "../services/QueueService";

const RowDetails: React.FC<{ props: { isExpanded: boolean, queueName: string } }> = ({props}) => {

    const [attributes, setAttributes] = useState<Array<Array<string>>>([]);

    function getQueueAttributes() {
        QueueService.getQueueAttributes(props.queueName).then(attributes => setAttributes(attributes))
    }

    return (
        <TableRow key={props.queueName + "-details"} style={{backgroundColor: "#f5f5f5"}}>
            <TableCell style={{paddingBottom: 0, paddingTop: 0}} colSpan={6}>
                <Collapse in={props.isExpanded} timeout="auto" unmountOnExit onEnter={() => getQueueAttributes()}>
                    <Box margin={1}>
                        <Typography variant="h6" gutterBottom component="div">
                            Queue attributes
                        </Typography>
                    </Box>
                    <Table size="small" aria-label="queue attributes">
                        <TableHead>
                            <TableRow>
                                <TableCell>Attribute Name</TableCell>
                                <TableCell align="left">Attribute Value</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {attributes.map((attribute) =>
                                (
                                    <TableRow key={attribute[0]}>
                                        <TableCell component="th" scope="row">{attribute[0]}</TableCell>
                                        <TableCell align="left">{attribute[1]}</TableCell>
                                    </TableRow>
                                )
                            )}
                        </TableBody>
                    </Table>
                </Collapse>
            </TableCell>
        </TableRow>
    )
}

export default RowDetails