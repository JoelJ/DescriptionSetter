package com.attask.jenkins.descriptionsetter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * User: joeljohnson
 * Date: 1/26/12
 * Time: 11:05 AM
 */
public class BuildDescriptionSetter extends BuildWrapper implements MatrixAggregatable {
	public final String descriptionTemplate;

	@DataBoundConstructor
	public BuildDescriptionSetter(String descriptionTemplate) {
		this.descriptionTemplate = descriptionTemplate;
	}

	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		final Map<String, String> newEnvVars = setDescription(build, listener);
		
		//Add any new Environment Variables permanently
		build.addAction(new EnvironmentContributingAction() {
			public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
				env.putAll(newEnvVars);
			}

			public String getIconFileName() { return null; }
			public String getDisplayName() { return null; }
			public String getUrlName() { return null; }
		});
		
		
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
				setDescription(build, listener);
				return true;
			}
		};
	}

	public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
		return new MatrixAggregator(build,launcher,listener) {
			@Override
			public boolean startBuild() throws InterruptedException, IOException {
				setDescription(build, listener);
				return super.startBuild();
			}

			@Override
			public boolean endBuild() throws InterruptedException, IOException {
				setDescription(build, listener);
				return super.endBuild();
			}
		};
	}

	private Map<String, String> setDescription(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
		if(build == null) {
			return Collections.emptyMap();
		}

		EnvVars envVars = build.getEnvironment(listener);
		SCM scm = build.getProject().getScm();

		Map<String, String> scmParameters;
		if(build.getWorkspace() == null || build.getWorkspace().getRemote() == null || build.getBuiltOn() == null || envVars == null) {
			scmParameters = Collections.emptyMap();
		} else {
			scmParameters = getScmParameters(scm, build.getWorkspace().getRemote(), build.getBuiltOn(), listener, envVars);
		}

		EnvVars environment = build.getEnvironment(listener);
		environment.putAll(scmParameters);
		setDescription(build, environment);
		
		return scmParameters;
	}

	private void setDescription(AbstractBuild build, EnvVars env) throws IOException {
		build.setDescription(env.expand(this.descriptionTemplate));
	}
	
	private Map<String, String> getScmParameters(SCM scm, String workspacePath, Node node, BuildListener listener, EnvVars envVars) {
		String gitExe = getGitExe(scm, node, listener);
		if(gitExe == null) return Collections.emptyMap();

		String revision = execute(gitExe, "log -n 1 --pretty=format:%H", workspacePath);
		String shortRevision = execute(gitExe, "log -n 1 --pretty=format:%h", workspacePath);
		String branch = execute(gitExe, "br --contains "+revision, workspacePath);
		String author = execute(gitExe, "log -n 1 --pretty=format:%an", workspacePath);

		Map<String, String> newEnvVars = new HashMap<String, String>();

		if(envVars.containsKey("GIT_BRANCH") && envVars.get("GIT_BRANCH") != null) {
			branch = envVars.get("GIT_BRANCH");
		}

		int index = branch.lastIndexOf("/");
		if(index > -1) {
			branch = branch.substring(index + 1);
		}

		if(branch.equals("HEAD")) {
			branch = "master";
		}

		newEnvVars.put("GIT_BRANCH", branch);
		newEnvVars.put("GIT_REVISION", revision);
		newEnvVars.put("GIT_REVISION_SHORT", shortRevision);
		newEnvVars.put("GIT_AUTHOR", author);
		return newEnvVars;
	}

	private String getGitExe(SCM scm, Node node, BuildListener listener) {
		Class<? extends SCM> scmClass = scm.getClass();
		if("hudson.plugins.git.GitSCM".equals(scmClass.getName())) {
			try {
				Method method = scmClass.getMethod("getGitExe", Node.class, TaskListener.class);
				Object result = method.invoke(scm, node, listener);
				return (String)result;
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	 	return null;
	}

	private String execute(String command, String params, String workspacePath) {
		try {
			Process p = Runtime.getRuntime().exec(command + " " + params, null, new File(workspacePath));
			InputStream inputStream = p.getInputStream();
			Scanner scanner = new Scanner(inputStream);
			if(!scanner.hasNextLine()) {
				return "";
			}
			String result = scanner.nextLine();
			if(result.contains("no branch") && scanner.hasNextLine()) {
				result = scanner.nextLine();
			}
			scanner.close();
			inputStream.close();
			return result;
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Set Description";
		}
	}
}
