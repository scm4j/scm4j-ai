package org.scm4j.ai.api;

import java.util.Map;

public interface IAction {
    String getInstallerClass();
    Map<String, Object> getParams();
}
