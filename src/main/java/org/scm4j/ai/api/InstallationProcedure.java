package org.scm4j.ai.api;

import java.util.List;

public class InstallationProcedure implements IInstallationProcedure {

    private List<IAction> actions;

    public InstallationProcedure(List<IAction> actions) {
        this.actions = actions;
    }

    @Override
    public List<IAction> getActions() {
        return actions;
    }
}
