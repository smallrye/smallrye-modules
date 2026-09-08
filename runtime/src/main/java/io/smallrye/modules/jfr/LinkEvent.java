package io.smallrye.modules.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category("Link")
@Label("Linkage Event")
@Description("Linkage Event")
public class LinkEvent extends Event {

    @Label("Module Name")
    public String moduleName;

    @Label("Module Version")
    public String moduleVersion;

    @Label("Link Stage")
    public String linkStage;

    public LinkEvent() {
    }
}
