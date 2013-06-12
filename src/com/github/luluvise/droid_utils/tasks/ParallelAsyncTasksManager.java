package com.github.luluvise.droid_utils.tasks;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.common.annotations.Beta;

/**
 * Small component that allows managing the lifecycle (execute, cancel) of
 * multiple {@link ParallelAsyncTask}s at the same time.
 * 
 * @since 1.5
 * @author Marco Salis
 * 
 * @param <Params>
 *            The Param type of the tasks to add
 */
@Beta
@NotThreadSafe
@SuppressWarnings("rawtypes")
public class ParallelAsyncTasksManager<T extends ParallelAsyncTask> {

	@Nonnull
	private final ArrayList<T> mManagedTasks;

	public ParallelAsyncTasksManager() {
		mManagedTasks = new ArrayList<T>();
	}

	public void addTask(@Nonnull T task) {
		mManagedTasks.add(task);
	}

	public void addAllTasks(@Nonnull Collection<? extends T> tasks) {
		mManagedTasks.addAll(tasks);
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	public <Params> T addAndExecute(@Nonnull T task, @Nullable Params... params) {
		addTask(task);
		return (T) task.parallelExec(params);
	}

	public void cancelAllTasks(boolean mayInterruptIfRunning) {
		for (T task : mManagedTasks) {
			if (task != null) {
				task.cancel(mayInterruptIfRunning);
			}
		}
		mManagedTasks.clear();
	}

}