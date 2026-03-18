
module io.smallrye.modules {
    requires transitive java.xml;
    requires java.logging;

    requires jdk.jfr;

    requires org.jboss.logging;

    requires io.smallrye.classfile;

    requires io.smallrye.common.constraint;
    requires io.smallrye.common.cpu;
    requires io.smallrye.common.os;
    requires transitive io.smallrye.common.resource;

    exports io.smallrye.modules;
    exports io.smallrye.modules.desc;
    exports io.smallrye.modules.jfr to jdk.jfr;

    uses java.util.logging.LogManager;
}
