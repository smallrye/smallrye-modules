package io.smallrye.modules.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category("Duplicate Class Definition")
@Label("Duplicate Class Definition")
@Description("Definition of a class was skipped because it was a duplicate")
public class DefineDuplicateEvent extends Event {
    @Label("Module Name")
    public String moduleName;
    @Label("Module Version")
    public String moduleVersion;
    @Label("Class Name")
    public String className;

    public DefineDuplicateEvent() {
    }
}
