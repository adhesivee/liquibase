package org.liquibase.eclipse.common.change.wizard;

import liquibase.migrator.change.Change;
import liquibase.migrator.change.DropIndexChange;
import org.eclipse.datatools.connectivity.sqm.core.rte.jdbc.JDBCTable;
import org.eclipse.datatools.modelbase.sql.constraints.Index;
import org.eclipse.datatools.modelbase.sql.schema.Database;
import org.eclipse.datatools.modelbase.sql.tables.Table;
import org.eclipse.jface.wizard.IWizardPage;

import java.sql.Connection;

public class DropIndexWizard extends BaseRefactorWizard {

	private Index index;
	private Table table;
	
	public DropIndexWizard(Database database, Connection connection, Index index) {
		super(database, connection);
		this.index = index;
	}

	@Override
	protected Change[] createChanges() {
		DropIndexChange change = new DropIndexChange();
		change.setIndexName(index.getName());
		change.setTableName(table.getName());
		
		return new Change[] { change };
	}

	@Override
	protected IWizardPage[] createPages() {
		return new IWizardPage[0];
	}

	@Override
	protected void refresh() {
		((JDBCTable)table).refresh();
	}
}
