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
	public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("Setting Description (Pre-checkout)");
		setDescription(build, listener);
	}

	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("Setting Description (Post-checkout)");
		setDescription(build, listener);
		
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
				listener.getLogger().println("Setting Description (Tear down)");
				setDescription(build, listener);
				return true;
			}
		};
	}

	public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
		return new MatrixAggregator(build,launcher,listener) {
			@Override
			public boolean startBuild() throws InterruptedException, IOException {
				listener.getLogger().println("Setting Description (Start build)");
				setDescription(super.build, super.listener);
				return super.startBuild();
			}

			@Override
			public boolean endBuild() throws InterruptedException, IOException {
				listener.getLogger().println("Setting Description (End build)");
				setDescription(super.build, super.listener);
				return super.endBuild();
			}
		};
	}

	private void setDescription(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
		if(build != null) {
			EnvVars environment = build.getEnvironment(listener);
			String description = environment.expand(this.descriptionTemplate);
			listener.getLogger().println(description);
			build.setDescription(description);
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
