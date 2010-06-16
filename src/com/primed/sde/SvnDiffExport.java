package com.primed.sde;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.primed.sde.command.Diff;
import com.primed.sde.command.Export;
import com.primed.sde.command.ExportAndZipRevision;
import com.primed.sde.command.Revision;
import com.primed.sde.command.Zip;
import java.util.ArrayList;

/**
 * Utility project used to patch a baseline export to a newer branch. 
 * Assumes you have already 'tagged' a branch for release.
 * The commands:
 * 
 * Create a diff.patch file
 * diff <old-branch-url> <new-branch-url> <diff-file>
 * 
 * Export each of the files described in the diff.patch to a target directory:
 * export <diff-file> <old-branch-url> <new-branch-url> <target-dir>
 * 
 * Create a revision file:
 * revision <new-branch-url> <revision-file-full-path>
 * 
 * Zip the new pack for transport via your mechanism ftp,ssh,xcopy...
 * zip <directory-to-zip>  
 *   
 * @author philip gloyne (philip.gloyne@gmail.com)
 * @since 25-JAN-2010
 */
public class SvnDiffExport {

    enum Command {
        diff, export, revision, zip, export_zip, export_zips
    };

    public static void main(String[] args) throws Exception {
        Long start = System.currentTimeMillis();

        SvnProperties properties = new SvnProperties(new File("svn.properties"));
        String svnUsername = properties.getSvnUsername();
        String svnPassword = properties.getSvnPassword();
        String svnDefaultUrl = properties.getSvnUrl();

        ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
        BasicAuthenticationManager bam = new BasicAuthenticationManager(svnUsername, svnPassword);
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();

//        System.err.println("arg length: " + args.length);
//        for (String arg : args) {
//            System.err.println("arg: " + arg);
//        }
        
        if (args.length == 0) {
            System.out.println("Arguments: <command> <command options>");
            System.out.println("Valid commands: diff, export, revision, zip, export_zip");
        } else {

            Command command = Command.valueOf(args[0]);
            System.err.println("Command: " + command);
            switch (command) {

                case diff:
                    System.out.println("diff..");
                    SVNURL oldBranch = SVNURL.parseURIEncoded(args[1]);
                    SVNURL newBranch = SVNURL.parseURIEncoded(args[2]);
                    String diff = args[3];
                    new Diff(new SVNDiffClient(bam, options), oldBranch, newBranch, diff).execute();
                    break;

                case export:
                    System.out.println("export..");
                    File diffFile = new File(args[1]);
                    if (!diffFile.exists()) {
                        throw new RuntimeException("diff file: " + args[1] + " not found.");
                    }
                    String oldBranchURL = args[2];
                    String newBranchURL = args[3];
                    String exportTo = args[4];
                    new Export(new SVNUpdateClient(bam, options), diffFile, oldBranchURL, newBranchURL, exportTo).execute();
                    break;

                case revision:
                    System.out.println("revision..");
                    SVNURL branch = SVNURL.parseURIEncoded(args[1]);
                    String target = args[2];
                    new Revision(new SVNWCClient(bam, options), branch, target).execute();
                    break;

                case zip:
                    System.out.println("zip..");
                    File zipTarget = new File(args[1]);
                    if (!zipTarget.exists()) {
                        throw new RuntimeException("zip target dir/file: " + args[1] + " not found.");
                    }
                    new Zip(zipTarget).execute();
                    break;

                case export_zips:
                case export_zip:
                    System.out.println("export_zip");

                    SVNURL srcBranch = null;
                    String revisionNumber = "";
                    if (args.length == 2) {
                        srcBranch = SVNURL.parseURIEncoded(svnDefaultUrl);
                        revisionNumber = args[1];

                    } else if (args.length == 3) {
                        srcBranch = SVNURL.parseURIEncoded(args[1]);
                        revisionNumber = args[2];
                    }

                    ArrayList<String> al = new ArrayList<String>();
                    if (revisionNumber.indexOf(",") > -1) {
                        System.err.println("exporting multiple revisions ...");
                        for (String rev : revisionNumber.split(",")) {
                            al.add(rev);
                        }
                    } else {
                        al.add(revisionNumber);
                    }

                    for (String rev: al) {
                        System.err.println("exporting revision " + rev);
                        new ExportAndZipRevision(bam, options, srcBranch, rev, command == Command.export_zips ).execute();
                    }


                    break;

            }
        }

        Long end = System.currentTimeMillis();
        System.out.println("finished. time: " + ((end - start) / 1000) + " seconds.");

    }
}
