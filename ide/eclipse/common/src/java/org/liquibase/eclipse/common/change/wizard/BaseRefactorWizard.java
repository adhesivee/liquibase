package org.liquibase.eclipse.common.change.wizard;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import liquibase.database.DatabaseFactory;
import liquibase.migrator.ChangeSet;
import liquibase.migrator.DatabaseChangeLog;
import liquibase.migrator.Migrator;
import liquibase.migrator.change.Change;
import liquibase.migrator.parser.MigratorSchemaResolver;
import liquibase.util.StringUtils;
import org.eclipse.core.commands.operations.OperationStatus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.datatools.modelbase.sql.schema.Database;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.liquibase.eclipse.common.LiquibasePreferences;
import org.liquibase.eclipse.common.change.wizard.page.ChangeMetaDataWizardPage;
import org.liquibase.eclipse.common.migrator.EclipseFileOpener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.Statement;

public abstract class BaseRefactorWizard extends Wizard {

	private ChangeMetaDataWizardPage metaDataPage = new ChangeMetaDataWizardPage();
	private Database database;
	private Connection connection;

	public BaseRefactorWizard(Database database, Connection connection) {
		this.database = database;
		this.connection = connection;
	}

	protected abstract IWizardPage[] createPages();

	protected abstract Change[] createChanges();

	public Database getDatabase() {
		return database;
	}

	public Connection getConnection() {
		return connection;
	}

	@Override
	public void addPages() {
		for (IWizardPage page : createPages()) {
			addPage(page);
		}

		addPage(metaDataPage);
	}

	@Override
	public boolean performFinish() {
		try {
			ProgressMonitorDialog pd = new ProgressMonitorDialog(this
					.getShell());
			pd.run(false, false, new IRunnableWithProgress() {

				public void run(IProgressMonitor monitor)
						throws InvocationTargetException, InterruptedException {
					monitor.beginTask("Refactoring Database", 100);

					try {
						Migrator migrator = new Migrator(LiquibasePreferences
								.getCurrentChangeLog(), new EclipseFileOpener());
						migrator.init(getConnection());
						
						monitor.subTask("Checking Control Tables");						
						migrator.getDatabase().checkDatabaseChangeLogTable(
								migrator);
						migrator.getDatabase().checkDatabaseChangeLogLockTable(
								migrator);
						monitor.worked(25);
						
						
						monitor.subTask("Executing Change");
						DatabaseChangeLog changeLog = new DatabaseChangeLog(
								migrator, LiquibasePreferences
										.getCurrentChangeLog());

						ChangeSet changeSet = new ChangeSet(metaDataPage
								.getId(), metaDataPage.getAuthor(),
								metaDataPage.isAlwaysRun(), metaDataPage
										.isRunOnChange(), changeLog,
								StringUtils.trimToNull(metaDataPage
										.getContext()), StringUtils
										.trimToNull(metaDataPage.getDbms()));
						changeSet.setComments(metaDataPage.getComments());

						for (Change change : createChanges()) {
							changeSet.addChange(change);
						}

						liquibase.database.Database liquibaseDatabase = DatabaseFactory
								.getInstance()
								.findCorrectDatabaseImplementation(connection);
						Statement statement = connection.createStatement();
						for (Change change : changeSet.getChanges()) {
							for (String sql : change
									.generateStatements(liquibaseDatabase)) {
								statement.execute(sql);
							}
						}
						statement.close();
						monitor.worked(25);
						
						monitor.subTask("Marking Change Set As Ran");
						migrator.markChangeSetAsRan(changeSet);
						monitor.worked(25);

						monitor.subTask("Writing to Change Log");
						DocumentBuilderFactory factory = DocumentBuilderFactory
								.newInstance();
						DocumentBuilder documentBuilder = factory
								.newDocumentBuilder();
						documentBuilder
								.setEntityResolver(new MigratorSchemaResolver());

						File file = new File(LiquibasePreferences
								.getCurrentChangeLogFileName());
						Document doc;
						if (!file.exists() || file.length() == 0) {
							doc = documentBuilder.newDocument();

							Element changeLogElement = doc
									.createElement("databaseChangeLog");
							changeLogElement
									.setAttribute("xmlns",
											"http://www.liquibase.org/xml/ns/dbchangelog/1.1");
							changeLogElement
									.setAttribute("xmlns:xsi",
											"http://www.w3.org/2001/XMLSchema-instance");
							changeLogElement
									.setAttribute(
											"xsi:schemaLocation",
											"http://www.liquibase.org/xml/ns/dbchangelog/1.1 http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-1.1.xsd");

							doc.appendChild(changeLogElement);
						} else {
							doc = documentBuilder.parse(file);
						}

						doc.getDocumentElement().appendChild(
								changeSet.createNode(doc));

						FileOutputStream out = new FileOutputStream(file);
						OutputFormat format = new OutputFormat(doc);
						format.setIndenting(true);
						XMLSerializer serializer = new XMLSerializer(out,
								format);
						serializer.asDOMSerializer();
						serializer.serialize(doc);
						
						monitor.done();
					} catch (Exception e) {
						throw new InvocationTargetException(e);
					}

				}
			});
			refresh();
		} catch (Throwable e) {
			e.printStackTrace();
			IStatus status = new OperationStatus(IStatus.ERROR,
					LiquibasePreferences.PLUGIN_ID, 1,
					"Error Creating Change: " + e.getMessage(), e);
			ErrorDialog.openError(this.getShell(), "Error",
					"Database Change Error", status);

			return true;
		}

		return true;
	}

	protected abstract void refresh();

}
