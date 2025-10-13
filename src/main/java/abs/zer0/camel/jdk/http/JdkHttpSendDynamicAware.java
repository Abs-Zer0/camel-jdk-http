package abs.zer0.camel.jdk.http;

import org.apache.camel.http.base.HttpSendDynamicAware;
import org.apache.camel.spi.annotations.SendDynamic;

@SendDynamic("jdk-http")
public class JdkHttpSendDynamicAware extends HttpSendDynamicAware {
}
