import React, {useEffect} from "react";

const useEffectOnce = (fun: React.EffectCallback) => useEffect(fun, [])

export default useEffectOnce