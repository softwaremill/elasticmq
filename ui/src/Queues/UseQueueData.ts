import RefreshQueuesData from "./RefreshQueuesData";
import useEffectOnce from "./UseEffectOnce";

const useQueueData = () => {
    const queuesStatisticsDataControl = RefreshQueuesData.useRefreshQueueData()

    useEffectOnce(() => {
        const fetchInitialStatistics = async () => {
            const initialStatistics = await queuesStatisticsDataControl.obtainInitialStatistics()
            queuesStatisticsDataControl.setQueuesOverallData((prevState) => {
                if (prevState.length === 0) {
                    return initialStatistics
                } else {
                    return prevState;
                }
            })
        }
        fetchInitialStatistics();
    });

    return queuesStatisticsDataControl.queuesOverallData;
}


export default useQueueData