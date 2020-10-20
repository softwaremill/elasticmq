package org.elasticmq.server;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "scala.runtime.Statics")
final class Target_scala_runtime_Statics {

    @Substitute
    public static void releaseFence() {
        UnsafeUtils.UNSAFE.storeFence();
    }
}

public class ScalaSubstitutions {
}
