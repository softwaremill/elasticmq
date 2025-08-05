import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import Collapse from "@material-ui/core/Collapse";
import Box from "@material-ui/core/Box";
import Typography from "@material-ui/core/Typography";
import Table from "@material-ui/core/Table";
import TableHead from "@material-ui/core/TableHead";
import { TableBody, Tabs, Tab } from "@material-ui/core";
import React, { useState } from "react";
import { getQueueAttributes } from "../services/QueueService";
import QueueMessagesList from "./QueueMessagesList";
import { QueueMessagesData } from "./QueueMessageData";

const RowDetails: React.FC<{
  isExpanded: boolean;
  queueName: string;
  queueData: QueueMessagesData;
  fetchQueueMessages: (queueName: string) => Promise<void>;
  deleteMessage: (
    queueName: string,
    messageId: string,
    receiptHandle: string
  ) => Promise<void>;
  updateMessageExpandedState: (
    queueName: string,
    messageId: string | null
  ) => void;
}> = ({
  isExpanded,
  queueName,
  queueData,
  fetchQueueMessages,
  deleteMessage,
  updateMessageExpandedState,
}) => {
  const [attributes, setAttributes] = useState<Array<Array<string>>>([]);
  const [activeTab, setActiveTab] = useState(0);

  async function fetchQueueAttributes() {
    const attributes = await getQueueAttributes(queueName);
    setAttributes(attributes);
  }

  const handleTabChange = (_event: React.ChangeEvent<{}>, newValue: number) => {
    setActiveTab(newValue);
  };

  interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
  }

  function TabPanel(props: TabPanelProps) {
    const { children, value, index, ...other } = props;

    return (
      <div
        role="tabpanel"
        hidden={value !== index}
        id={`queue-tabpanel-${index}`}
        aria-labelledby={`queue-tab-${index}`}
        {...other}
      >
        {value === index && <Box>{children}</Box>}
      </div>
    );
  }

  return (
    <TableRow
      key={`${queueName}-details`}
      style={{ backgroundColor: "#f5f5f5" }}
    >
      <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={6}>
        <Collapse
          in={isExpanded}
          timeout="auto"
          unmountOnExit
          onEnter={fetchQueueAttributes}
        >
          <Box margin={1}>
            <Tabs
              value={activeTab}
              onChange={handleTabChange}
              aria-label="queue details tabs"
            >
              <Tab
                label="Queue Attributes"
                id="queue-tab-0"
                aria-controls="queue-tabpanel-0"
              />
              <Tab
                label="Messages"
                id="queue-tab-1"
                aria-controls="queue-tabpanel-1"
              />
            </Tabs>

            <TabPanel value={activeTab} index={0}>
              <Box pt={2}>
                <Typography variant="h6" gutterBottom component="div">
                  Queue attributes
                </Typography>
                <Table size="small" aria-label="queue attributes">
                  <TableHead>
                    <TableRow>
                      <TableCell>Attribute Name</TableCell>
                      <TableCell align="left">Attribute Value</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {attributes.map((attribute) => (
                      <TableRow key={attribute[0]}>
                        <TableCell component="th" scope="row">
                          {attribute[0]}
                        </TableCell>
                        <TableCell align="left">{attribute[1]}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={1}>
              <Box pt={2}>
                <QueueMessagesList
                  queueName={queueName}
                  messages={queueData.messages}
                  loading={queueData.messagesLoading}
                  error={queueData.messagesError}
                  onRefreshMessages={fetchQueueMessages}
                  onDeleteMessage={deleteMessage}
                  updateMessageExpandedState={updateMessageExpandedState}
                />
              </Box>
            </TabPanel>
          </Box>
        </Collapse>
      </TableCell>
    </TableRow>
  );
};

export default RowDetails;
