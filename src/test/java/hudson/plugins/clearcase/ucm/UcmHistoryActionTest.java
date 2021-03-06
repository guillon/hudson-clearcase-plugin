/**
 * The MIT License
 *
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.clearcase.ucm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.AbstractClearCaseScm;
import hudson.plugins.clearcase.ClearCaseUcmSCM;
import hudson.plugins.clearcase.ClearCaseUcmSCMDummy;
import hudson.plugins.clearcase.ClearTool;
import hudson.plugins.clearcase.ClearToolLauncher;
import hudson.plugins.clearcase.history.DefaultFilter;
import hudson.plugins.clearcase.history.DestroySubBranchFilter;
import hudson.plugins.clearcase.history.FileFilter;
import hudson.plugins.clearcase.history.Filter;
import hudson.plugins.clearcase.history.FilterChain;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class UcmHistoryActionTest {

    private Mockery context;
    private Mockery classContext;
    private ClearTool cleartool;
    private ClearToolLauncher clearToolLauncher;
    private Launcher launcher;
    private ClearCaseUcmSCM.ClearCaseUcmScmDescriptor clearCaseUcmScmDescriptor;
    private AbstractBuild build;

    @Before
    public void setUp() throws Exception {
        classContext = new JUnit4Mockery() {
                {
                    setImposteriser(ClassImposteriser.INSTANCE);
                }
            };
        launcher = classContext.mock(Launcher.class);
        clearCaseUcmScmDescriptor = classContext.mock(ClearCaseUcmSCM.ClearCaseUcmScmDescriptor.class);
        context = new JUnit4Mockery();
        cleartool = context.mock(ClearTool.class);
        clearToolLauncher = context.mock(ClearToolLauncher.class);
        build = classContext.mock(AbstractBuild.class);
        
    }

    /*
     * Below are taken from DefaultPollActionTest
     */

    @Test
    public void assertSeparateBranchCommands() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branchone")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader("")));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branchtwo")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader("\"20071015.151822\" \"user\" \"Customer\\DataSet.xsd\" \"\\main\\sit_r6a\\2\" \"create version\" \"mkelem\" \"activity\" ")));
                }
            });

        UcmHistoryAction action = createUcmHistoryAction();
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branchone", "branchtwo"}, new String[]{"vobpath"});
        assertTrue("The getChanges() method did not report a change", hasChange);
    }

    @Test
    public void assertSuccessfulParse() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20071015.151822\" \"username\" \"Customer\\DataSet.xsd\" \"\\main\\sit_r6a\\1\" \"create version\"  \"mkelem\" \"activity\" "
                                                      + "\"20071015.151822\" \"username\" \"Customer\\DataSet.xsd\" \"\\main\\sit_r6a\\2\" \"create version\"  \"mkelem\" \"activity\" ")));
                }
            });

        UcmHistoryAction action = createUcmHistoryAction();
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        assertTrue("The getChanges() method did not report a change", hasChange);
        context.assertIsSatisfied();
    }

    @Test
    public void assertIgnoringErrors() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader("cleartool: Error: Not an object in a vob: \"view.dat\".\n")));
                }
            });
        UcmHistoryAction action = new UcmHistoryAction(cleartool,false,new DefaultFilter(), null, null, null);
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        assertFalse("The getChanges() method reported a change", hasChange);
    }

    @Test
    public void assertIgnoringVersionZero() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader("\"20071015.151822\" \"username\" \"Customer\\DataSet.xsd\" \"\\main\\sit_r6a\\0\" \"create version\"  \"mkelem\" \"activity\" ")));
                }
            });
        UcmHistoryAction action = new UcmHistoryAction(cleartool,false,new DefaultFilter(), null, null, null);
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        assertFalse("The getChanges() method reported a change", hasChange);
    }

    @Test
    public void assertIgnoringDestroySubBranchEvent() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080326.110739\" \"username\" \"vobs/gtx2/core/src/foo/bar/MyFile.java\" \"/main/feature_1.23\" \"destroy sub-branch \"esmalling_branch\" of branch\" \"rmbranch\" \"activity\" ")));
                }
            });
        UcmHistoryAction action = new UcmHistoryAction(cleartool,false,new DestroySubBranchFilter(), null, null, null);
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        assertFalse("The getChanges() method reported a change", hasChange);
    }

    @Test
    public void assertNotIgnoringDestroySubBranchEvent() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080326.110739\" \"username\" \"vobs/gtx2/core/src/foo/bar/MyFile.java\" \"/main/feature_1.23\" \"destroy sub-branch \"esmalling_branch\" of branch\" \"rmbranch\" \"activity\" ")));
                }
            });


        UcmHistoryAction action = createUcmHistoryAction();
        boolean hasChange = action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        assertTrue("The getChanges() method reported a change", hasChange);
    }

    @Test(expected=IOException.class)
    public void assertReaderIsClosed() throws Exception {
        final StringReader reader = new StringReader("\"20071015.151822\" \"username\" \"Customer\\DataSet.xsd\" \"\\main\\sit_r6a\\1\" \"create version\"  \"mkelem\" \"activity\" ");
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    ignoring(cleartool).lshistory(with(aNonNull(String.class)), with(aNull(Date.class)), with(equal("view")), with(equal("branch")), with(equal(new String[]{"vobpath"})), with(equal(false)));
                    will(returnValue(reader));
                }
            });

        UcmHistoryAction action = createUcmHistoryAction();
        action.hasChanges(null, "view", "viewTag", new String[]{"branch"}, new String[]{"vobpath"});
        reader.ready();
    }



    /*
     * Below are taken from UcmBaseChangelogActionTest
     */
    @Test
    public void assertFormatContainsComment() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(equal("\\\"%Nd\\\" \\\"%u\\\" \\\"%En\\\" \\\"%Vn\\\" \\\"%e\\\" \\\"%o\\\" \\\"%[activity]p\\\" \\n%c\\n")),
                                             with(any(Date.class)), with(any(String.class)), with(any(String.class)), 
                                             with(any(String[].class)), with(any(boolean.class)));
                    will(returnValue(new StringReader("")));
                }
            });
        
        UcmHistoryAction action = createUcmHistoryAction();
        action.getChanges(new Date(), "viewPath", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
    }

    private UcmHistoryAction createUcmHistoryAction() {
        return new UcmHistoryAction(cleartool,false,null, null, null, null);
    }
    
    @Test
    public void assertDestroySubBranchEventIsIgnored() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080509.140451\" " +
                                                      "\"user\"" +
                                                      "\"vobs/projects/Server//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/Release_3_3_jdk5/2\" " +
                                                      "\"destroy sub-branch \"esmalling_branch\" of branch\" " +
                                                      "\"checkin\" \"activity\" ")));
                }
            });
        UcmHistoryAction action = new UcmHistoryAction(cleartool,false,new DestroySubBranchFilter(), null, null, null);
        @SuppressWarnings("unchecked")
        List<UcmActivity> activities = (List<UcmActivity>) action.getChanges(null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        assertEquals("There should be 0 activity", 0, activities.size());
    }

    @Test
    public void assertExcludedRegionsAreIgnored() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080509.140451\" " +
                                                      "\"user\"" +
                                                      "\"vobs/projects/Server//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/activityA/2\" " +
                                                      "\"create version\" " +
                                                      "\"checkin\" \"activityA\" " +
                                                      "\"20080509.140451\" " +
                                                      "\"user\"" +
                                                      "\"vobs/projects/Client//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/activityB/2\" " +
                                                      "\"create version\" " +
                                                      "\"checkin\" \"activityB\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("activityA")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Activity A info \" " +
                                                      "\"activityA\" " +
                                                      "\"bob\" " +
                                                      "\"maven2_Release_3_3.20080421.154619\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("activityB")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Activity B info \" " +
                                                      "\"activityB\" " +
                                                      "\"bob\" " +
                                                      "\"maven2_Release_3_3.20080421.154619\" ")));

                }
            });
        
        List<Filter> filters = new ArrayList<Filter>();

        filters.add(new DefaultFilter());
        filters.add(new FileFilter(FileFilter.Type.DoesNotContainRegxp, "Server"));
        UcmHistoryAction action = new UcmHistoryAction(cleartool,false,new FilterChain(filters), null, null, null);
        @SuppressWarnings("unchecked")
        List<UcmActivity> activities = (List<UcmActivity>) action.getChanges(null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        assertEquals("There should be 1 activity", 1, activities.size());
    }

    
    @Test
    public void assertParsingOfNonIntegrationActivity() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080509.140451\" " +
                                                      "\"username\" "+
                                                      "\"vobs/projects/Server//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/Release_3_3_jdk5/2\" " +                        
                                                      "\"create directory version\" " +
                                                      "\"checkin\"  " +
                                                      "\"Release_3_3_jdk5.20080509.155359\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("Release_3_3_jdk5.20080509.155359")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Convert to Java 6\" " +
                                                      "\"Release_3_3_jdk5\" " +
                                                      "\"bob\" ")));
                }
            });
        
        UcmHistoryAction action = createUcmHistoryAction();
        List<UcmActivity> activities = (List<UcmActivity>) action.getChanges(null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        assertEquals("There should be 1 activity", 1, activities.size());
        UcmActivity activity = activities.get(0);
        assertEquals("Activity name is incorrect", "Release_3_3_jdk5.20080509.155359", activity.getName());
        assertEquals("Activity headline is incorrect", "Convert to Java 6", activity.getHeadline());
        assertEquals("Activity stream is incorrect", "Release_3_3_jdk5", activity.getStream());
        assertEquals("Activity user is incorrect", "bob", activity.getUser());
    }
    
    @Test
    public void assertParsingOfIntegrationActivity() throws Exception {
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080509.140451\" " +
                                                      "\"username\"  " +
                                                      "\"vobs/projects/Server//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/Release_3_3_jdk5/2\" " +                        
                                                      "\"create directory version\" " +
                                                      "\"checkin\" " +
                                                      "\"deliver.Release_3_3_jdk5.20080509.155359\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("deliver.Release_3_3_jdk5.20080509.155359")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Convert to Java 6\" " +
                                                      "\"Release_3_3_jdk5\" " +
                                                      "\"bob\" " +
                                                      "\"maven2_Release_3_3.20080421.154619 maven2_Release_3_3.20080421.163355\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("maven2_Release_3_3.20080421.154619")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Deliver maven2\" " +
                                                      "\"Release_3_3\" " +
                                                      "\"doe\" " +
                                                      "\"John Doe\" ")));
                    one(cleartool).lsactivity(
                                              with(equal("maven2_Release_3_3.20080421.163355")), 
                                              with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Deliver maven3\" " +
                                                      "\"Release_3_3\" " +
                                                      "\"doe\" " +
                                                      "\"John Doe\" ")));
                }
            });
        
        UcmHistoryAction action = createUcmHistoryAction();
        List<UcmActivity> activities = (List<UcmActivity>) action.getChanges(null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        assertEquals("There should be 1 activity", 1, activities.size());
        UcmActivity activity = activities.get(0);
        assertEquals("Activity name is incorrect", "deliver.Release_3_3_jdk5.20080509.155359", activity.getName());
        assertEquals("Activity headline is incorrect", "Convert to Java 6", activity.getHeadline());
        assertEquals("Activity stream is incorrect", "Release_3_3_jdk5", activity.getStream());
        assertEquals("Activity user is incorrect", "bob", activity.getUser());
        
        List<UcmActivity> subActivities = activity.getSubActivities();
        assertEquals("There should be 2 sub activities", 2, subActivities.size());
        assertEquals("Name of first sub activity is incorrect", "maven2_Release_3_3.20080421.154619", subActivities.get(0).getName());
        assertEquals("Name of second sub activity is incorrect", "maven2_Release_3_3.20080421.163355", subActivities.get(1).getName());
    }

    @Test(expected=IOException.class)
    public void assertLshistoryReaderIsClosed() throws Exception {
        final StringReader lshistoryReader = new StringReader(
                                                              "\"20080509.140451\" " +
                                                              "\"username\" " +
                                                              "\"vobs/projects/Server//config-admin-client\" " +
                                                              "\"/main/Product/Release_3_3_int/Release_3_3_jdk5/2\" " +                
                                                              "\"create directory version\" " +
                                                              "\"checkin\" "+
                                                              "\"Release_3_3_jdk5.20080509.155359\" ");
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(lshistoryReader));
                    ignoring(cleartool).lsactivity(
                                                   with(equal("Release_3_3_jdk5.20080509.155359")), 
                                                   with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(new StringReader("\"Convert to Java 6\" " +
                                                      "\"Release_3_3_jdk5\" " +
                                                      "\"bob\" ")));
                }
            });
        
        UcmHistoryAction action = createUcmHistoryAction();
        action.getChanges( null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        lshistoryReader.ready();
    }

    @Test(expected=IOException.class)
    public void assertLsactivityReaderIsClosed() throws Exception {
        final StringReader lsactivityReader = new StringReader("\"Convert to Java 6\" " +
                                                               "\"Release_3_3_jdk5\" " +
                                                               "\"bob\" ");
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    one(cleartool).lshistory(with(any(String.class)), with(aNull(Date.class)), 
                                             with(equal("IGNORED")), with(equal("Release_2_1_int")), with(equal(new String[]{"vobs/projects/Server"})), with(equal(false)));
                    will(returnValue(new StringReader(
                                                      "\"20080509.140451\" " +
                                                      "\"username\" " +
                                                      "\"vobs/projects/Server//config-admin-client\" " +
                                                      "\"/main/Product/Release_3_3_int/Release_3_3_jdk5/2\" " +                        
                                                      "\"create directory version\" " +
                                                      "\"checkin\"  "+ 
                                                      "\"Release_3_3_jdk5.20080509.155359\" " )));
                    ignoring(cleartool).lsactivity(
                                                   with(equal("Release_3_3_jdk5.20080509.155359")), 
                                                   with(aNonNull(String.class)),with(aNonNull(String.class)));
                    will(returnValue(lsactivityReader));
                }
            });
        
        UcmHistoryAction action = createUcmHistoryAction();
        action.getChanges(null, "IGNORED", "viewTag", new String[]{"Release_2_1_int"}, new String[]{"vobs/projects/Server"});
        lsactivityReader.ready();
    }

    @Bug(5342)
    @Test
    public void testUCMTrailingSlashesInLoadRules() throws Exception {
        AbstractClearCaseScm scm = new ClearCaseUcmSCMDummy("jcp_v13.1_be_int@\\june2008_recover", "\\be_rec\\config\\\r\n\\be_rec\\access\\\r\n"
                                                       + "\\be_rec\\admins\\\r\n\\be_rec\\be\\\r\n\\be_rec\\buildservices\\\r\n"
                                                       + "\\be_rec\\uf\\\r\n\\be_rec\\sef\\\r\n\\be_rec\\jwash\\", "stromp_be_builc", false, "M:\\",
                                                       null, true, true, false, null, null, null, false,
                                                       cleartool, clearCaseUcmScmDescriptor);

        classContext.checking(new Expectations() {
                {
                    allowing(launcher).isUnix(); will(returnValue(false));
                }
            });
        context.checking(new Expectations() {
                {
                    allowing(cleartool).doesViewExist(with(equal("viewTag"))); will(returnValue(true));
                    allowing(clearToolLauncher).getLauncher();
                    will(returnValue(launcher));
                    one(cleartool).lshistory(with(aNonNull(String.class)),
                                             with(aNull(Date.class)),
                                             with(equal("stromp_be_builc")),
                                             with(equal("jcp_v13.1_be_int")),
                                             with(any(String[].class)),
                                             with(any(boolean.class)));
                    will(returnValue(new StringReader(
                                                      "\"20100120.114845\" \"lmiguet\" "
                                                      + "\"D:\\java\\hudson\\jobs\\stromp_be_test\\workspace\\stromp_be_builc\\be_rec\\be\\airshopper\\legacy\\src\\main\\java\\com\\amadeus\\ocg\\standard\\business\\farecommon\\entity\\PricingCommandOutput.java\" "
                                                      + "\"\\main\\jcp_v13.1_be_int\\4\" \"create version\" \"checkin\" \"PTR3693254_WWW_AeRE_V131_INTCR_3313592-_Code_Review\" ")));
                }
            });


        UcmHistoryAction action = new UcmHistoryAction(cleartool, false, scm.configureFilters(new VariableResolver.ByMap<String>(new HashMap<String, String>()), build, launcher), null, null, null);
        action.setExtendedViewPath("D:\\java\\hudson\\jobs\\stromp_be_test\\workspace\\stromp_be_builc\\");
        boolean hasChange = action.hasChanges(null, "stromp_be_builc", "viewTag", new String[]{"jcp_v13.1_be_int"}, scm.getViewPaths(null, null, launcher));
        assertTrue("The hasChanges() method did not report a change", hasChange);
    }
        
}
