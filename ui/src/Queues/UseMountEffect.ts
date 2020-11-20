import React, {useEffect} from "react";

const useMountEffect = (fun: React.EffectCallback) => useEffect(fun, [])

export default useMountEffect