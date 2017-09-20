package org.scm4j.ai.api;

import lombok.Data;

import java.util.List;

@Data
public class InstallationProcedure implements IInstallationProcedure {

    final private List<IAction> actions;

}
