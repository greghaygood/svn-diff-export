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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;


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

    public static void main(String[] fullArgs) throws Exception {
        Long start = System.currentTimeMillis();

        Options options = new Options();
        options.addOption("f", "file", true, "config file with svn information");
        options.addOption("u", "url", true, "the SVN URL on which to operate");
        options.addOption("i", "input", true, "input file");
        options.addOption("o", "output", true, "output file or folder (depending on operation)");

        //        options.addOption("u1", "url1", true, "the first SVN URL on which to operate");
//        options.addOption("u2", "url2", true, "the second SVN URL on which to operate");

        options.addOption("1", "old", true, "the old (source) SVN URL on which to operate");
        options.addOption("2", "new", true, "the new (destination) SVN URL on which to operate");

        CommandLineParser parser = new GnuParser();
        CommandLine cmd = parser.parse( options, fullArgs);

        String propertiesFileName = cmd.getOptionValue("f", "svn.properties");

        System.err.println("Using config file: " + propertiesFileName);

        SvnProperties properties = new SvnProperties(new File(propertiesFileName));
        String svnUsername = properties.getSvnUsername();
        String svnPassword = properties.getSvnPassword();
        String svnDefaultUrl = properties.getSvnUrl();

        ISVNOptions svnOptions = SVNWCUtil.createDefaultOptions(true);
        BasicAuthenticationManager bam = new BasicAuthenticationManager(svnUsername, svnPassword);
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();

//        System.err.println("arg length: " + args.length);
//        for (String arg : args) {
//            System.err.println("arg: " + arg);
//        }

        String[] args = cmd.getArgs();
        for (String opt : args) {
            //System.err.println("remaining arg: " + opt);
        }
        
        if (args.length == 0) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "SvnDiffExport <cmd> <options>", options );
            System.out.println("Valid commands: diff, export, revision, zip, export_zip");

        } else {

            Command command = Command.valueOf(args[0]);
            System.err.println("Command: " + command);
            switch (command) {

                case diff:
                    System.out.println("diff..");
                    SVNURL oldBranch = SVNURL.parseURIEncoded(cmd.getOptionValue("1"));
                    SVNURL newBranch = SVNURL.parseURIEncoded(cmd.getOptionValue("2"));
                    String diff = cmd.getOptionValue("o");
                    new Diff(new SVNDiffClient(bam, svnOptions), oldBranch, newBranch, diff).execute();
                    break;

                case export:
                    System.out.println("export..");
                    File diffFile = new File(cmd.getOptionValue("i"));
                    if (!diffFile.exists()) {
                        throw new RuntimeException("diff file: " + args[1] + " not found.");
                    }
                    String oldBranchURL = cmd.getOptionValue("1");
                    String newBranchURL = cmd.getOptionValue("2");
                    String exportTo = cmd.getOptionValue("o");
                    new Export(new SVNUpdateClient(bam, svnOptions), diffFile, oldBranchURL, newBranchURL, exportTo).execute();
                    break;

                case revision:
                    System.out.println("revision..");
                    SVNURL branch = SVNURL.parseURIEncoded(cmd.getOptionValue("u"));
                    String target = args[2];
                    new Revision(new SVNWCClient(bam, svnOptions), branch, target).execute();
                    break;

                case zip:
                    System.out.println("zip..");
                    File zipTarget = new File(cmd.getOptionValue("o"));
                    if (!zipTarget.exists()) {
                        throw new RuntimeException("zip target dir/file: " + cmd.getOptionValue("o") + " not found.");
                    }
                    new Zip(zipTarget).execute();
                    break;

                case export_zips:
                case export_zip:
                    System.out.println("export_zip");

                    SVNURL srcBranch = SVNURL.parseURIEncoded(cmd.getOptionValue("u", svnDefaultUrl));;

                    ArrayList<String> al = new ArrayList<String>();
                    ArrayList<String> altemp = new ArrayList<String>();
                    if (args[1].indexOf(",") > -1) {
                        System.err.println("exporting multiple revisions ...");
                        for (String rev : args[1].split(",")) {
                            altemp.add(rev);
                        }
                    } else {
                        String arg = null;
                        for (int i=1; i<args.length; i++) {
                            altemp.add(args[i]);
                        }
                    }
                    for (String rev: altemp) {
                        if (rev.indexOf("-") > -1) {
                            System.err.println("exporting a range ...");
                            String[] revs = rev.split("-");
                            if (revs.length != 2) {
                              System.err.println("ERROR: invalid range given: " + rev);
                              continue;
                            }
                            int range_start = Integer.parseInt(revs[0].trim());
                            int range_end = Integer.parseInt(revs[1].trim());
                            for (int i= range_start; i <= range_end; i++) {
                                al.add("" + i);
                            }
                        } else {
                            al.add(rev);
                        }
                    }

                    for (String rev: al) {
                        System.err.println("exporting revision " + rev);
                        new ExportAndZipRevision(bam, svnOptions, srcBranch, rev, command == Command.export_zips ).execute();
                    }


                    break;

            }
        }

        Long end = System.currentTimeMillis();
        System.out.println("finished. time: " + ((end - start) / 1000) + " seconds.");

    }
}
