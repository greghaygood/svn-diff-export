package com.primed.sde.command;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.primed.sde.command.Diff.DiffFile;
import com.primed.sde.command.Revision.RevisionFile;

public class ExportAndZipRevision implements ISVNDiffStatusHandler {

    protected static String NEW_LINE = System.getProperty("line.separator");
    protected static String PATH_SEP = "/"; //System.getProperty("path.separator");
    private static String DIFF_FILE_NAME = "svn-export.diff";
    private static String DIFF_FILE;
    private static String TARGET_FOLDER = "export";
    private final ISVNOptions options;
    private final BasicAuthenticationManager bam;
    private final SVNURL branch;
    private final boolean individualZipFiles;
    private static boolean alreadyCleanedTargetFolder = false;
    private String revisionNumberString;
    private String previousRevisionString;
    private SVNRevision revision;
    private SVNRevision previousRevision;
    private List<String> changes;
    private SVNUpdateClient updateClient;

    /**
     * Reads and exports the content of diff.patch.
     *
     * @param client
     * @param diff the diff.patch
     * @param oldBranch the older branch (should be the same at the current baseline export).
     * @param newBranch the new branch which you wish you take the baseline to.
     * @param target the directory to output the exports
     * @throws SVNException
     * @throws IOException
     */
    public ExportAndZipRevision(BasicAuthenticationManager bam, ISVNOptions options, SVNURL branch, String revisionNumber, boolean individualZipFiles) throws SVNException, IOException {
        this.bam = bam;
        this.options = options;
        this.branch = branch;
        this.revisionNumberString = revisionNumber;
        this.individualZipFiles = individualZipFiles;

        this.changes = new ArrayList<String>();

        if (!individualZipFiles && !alreadyCleanedTargetFolder) {
            cleanTargetFolder();
            alreadyCleanedTargetFolder = true;
        }
    }

    public ExportAndZipRevision(BasicAuthenticationManager bam, ISVNOptions options, SVNURL branch, String revisionNumber) throws SVNException, IOException {

        this(bam, options, branch, revisionNumber, true);
    }

    /**
     * Read and exports all added and modified files.
     *
     * @throws SVNException
     * @throws IOException
     * @throws InterruptedException
     */
    public void execute() throws SVNException, IOException, InterruptedException {

        long revisionNumber = new Long(revisionNumberString);
        this.revision = SVNRevision.create(revisionNumber);
        long previousRevisionNumber = revisionNumber - 1;
        this.previousRevision = SVNRevision.create(previousRevisionNumber);
        this.previousRevisionString = String.valueOf(previousRevisionNumber);       

        if (this.individualZipFiles) {
            cleanTargetFolder();
        }

        new File(TARGET_FOLDER).mkdirs();

        SVNDiffClient diffClient = new SVNDiffClient(bam, options);
        diffClient.doDiffStatus(branch, previousRevision, branch, revision, SVNDepth.INFINITY, false, this);

        this.updateClient = new SVNUpdateClient(bam, options);
        for (String change : changes) {
            export(change);
        }

        SVNWCClient infoClient = new SVNWCClient(bam, options);
        SVNInfo info = infoClient.doInfo(branch, previousRevision, revision);
        new RevisionFile(TARGET_FOLDER + PATH_SEP + "revision-" + revisionNumberString + ".txt", info);

        String zipFileName = TARGET_FOLDER + PATH_SEP + ".." + PATH_SEP + "export-" + revisionNumberString + ".zip";
        File zipTarget = new File(TARGET_FOLDER);
        if (!zipTarget.exists()) {
            throw new RuntimeException("zip file: " + zipTarget.toString() + " not found!");
        }
        new Zip(zipTarget, "export-" + revisionNumberString + ".zip").execute();
    }

    private void cleanTargetFolder() {
        System.err.println("Cleaning out target folder ...");
        File d = new File(TARGET_FOLDER).getAbsoluteFile();
        deleteDirectory(d);
    }
    
    public void handleDiffStatus(SVNDiffStatus svnDiffStatus) throws SVNException {
        if (svnDiffStatus.getModificationType().equals(SVNStatusType.STATUS_MODIFIED)
                || svnDiffStatus.getModificationType().equals(SVNStatusType.STATUS_ADDED)
                || svnDiffStatus.getModificationType().equals(SVNStatusType.STATUS_DELETED)) {

            changes.add(encodeStatus(svnDiffStatus.getModificationType()) + " " + svnDiffStatus.getURL());
        }
    }

    private String encodeStatus(SVNStatusType modificationType) {
        if (modificationType.equals(SVNStatusType.STATUS_MODIFIED)) {
            return "M";
        }
        if (modificationType.equals(SVNStatusType.STATUS_ADDED)) {
            return "A";
        }
        if (modificationType.equals(SVNStatusType.STATUS_DELETED)) {
            return "D";
        }
        return "?";
    }

    /**
     * Called for each line in the diff.patch. Exports a single file to the target.
     *
     * @param change
     * @throws IOException
     * @throws InterruptedException
     * @throws SVNException
     */
    private void export(String change) throws IOException, InterruptedException, SVNException {
        System.err.println("export(" + change + ")");
        String operation = change.trim().charAt(0) + "";
        String path = change.trim().substring(1).trim();
        String exportTo = TARGET_FOLDER + path.replaceFirst(branch.toString(), "");
        File f = new File(exportTo);
        File d = new File(f.getAbsolutePath().replaceFirst(f.getName(), ""));
        System.err.println("exporting to: " + d.toString());
        d.mkdirs();

         SVNURL location= SVNURL.parseURIEncoded(path);
		
		if (operation.equalsIgnoreCase("D")) {
            // Handle deletes if you wish, be careful of directories.
        } else if (operation.equalsIgnoreCase("M") || operation.equalsIgnoreCase("A")) {
            System.err.println("exporting path: " + f.toString());
            updateClient.doExport(location, d, revision, revision, "native", true, SVNDepth.EMPTY);

        } else {
            throw new IOException("Error! Malformed operation: " + operation);
        }

    }

    static public boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

    class RevisionFile extends File {

        private static final long serialVersionUID = -850805471980707152L;

        RevisionFile(String pathname, SVNInfo info) throws IOException {
            super(pathname);
            createNewFile();
            Writer output = new BufferedWriter(new FileWriter(this));
            StringBuffer sb = new StringBuffer();
            String branch = info.getURL().toString().replaceFirst("http://.*/", "");
            sb.append("Branch:   ").append(branch).append(NEW_LINE);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
            String updated = sdf.format(info.getCommittedDate());
            sb.append("Updated:  ").append(updated).append(NEW_LINE);
            String revision = info.getCommittedRevision().toString();
            sb.append("Revision: ").append(revision).append(NEW_LINE);

            output.write(sb.toString());
            output.close();
        }
    }
}
