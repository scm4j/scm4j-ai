package org.scm4j.ai.api;

import java.util.Map;

public class Action implements IAction {

    private String installerClass;
    private Map<String, Object> params;

    public Action(String installerClass, Map<String, Object> params) {
        this.installerClass = installerClass;
        this.params = params;
    }

    public Action(String installerClass) {
        this.installerClass = installerClass;
    }

    @Override
    public String getInstallerClass() {
        return null;
    }

    @Override
    public Map<String, Object> getParams() {
        return null;
    }

}
