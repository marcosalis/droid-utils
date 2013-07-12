package com.github.luluvise.droid_utils.tasks;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import android.os.AsyncTask;

import com.google.common.annotations.Beta;

/**
 * Small component that allows managing the lifecycle (execution and
 * cancellation) of multiple {@link ParallelAsyncTask}s at the same time.
 * 
 * @since 1.5
 * @author Marco Salis
 */
@Beta
@NotThreadSafe
public class ParallelAsyncTasksManager {

	@Nonnull
	private final ArrayList<ParallelAsyncTask<?, ?, ?>> mManagedTasks;

	/**
	 * Constructor for a {@link ParallelAsyncTasksManager}
	 */
	public ParallelAsyncTasksManager() {
		mManagedTasks = new ArrayList<ParallelAsyncTask<?, ?, ?>>();
	}

	/**
	 * Adds a {@link ParallelAsyncTask} to the manager without executing it
	 * 
	 * @param task
	 *            The task to add to the manager
	 */
	public void addTask(@Nonnull ParallelAsyncTask<?, ?, ?> task) {
		mManagedTasks.add(task);
	}

	/**
	 * Adds a {@link ParallelAsyncTask} to the manager and executes it.
	 * 
	 * @param task
	 *            The task to add to the manager and execute
	 * @param params
	 *            The params to pass to
	 *            {@link ParallelAsyncTask#parallelExec(Object...)}
	 * @return The executed {@link ParallelAsyncTask}
	 */
	@Nonnull
	public <Params> ParallelAsyncTask<Params, ?, ?> addAndExecute(
			@Nonnull ParallelAsyncTask<Params, ?, ?> task, @Nullable Params... params) {
		addTask(task);
		return (ParallelAsyncTask<Params, ?, ?>) task.parallelExec(params);
	}

	/**
	 * Adds a collection of {@link ParallelAsyncTask} to the manager without
	 * executing them.
	 * 
	 * @param tasks
	 *            A {@link Collection} of async tasks
	 */
	public void addAllTasks(@Nonnull Collection<? extends ParallelAsyncTask<?, ?, ?>> tasks) {
		mManagedTasks.addAll(tasks);
	}

	/**
	 * Cancels all the tasks whose class is the same as the passed one. The
	 * comparison is made by equality with the tasks
	 * {@link ParallelAsyncTask#getClass()}.
	 * 
	 * Note that the tasks are cancelled but not removed from the manager.
	 * {@link #cancelAllTasks(boolean)} has still to be called.
	 * 
	 * @param taskClass
	 *            The {@link Class} of the tasks to cancel
	 * @param mayInterruptIfRunning
	 *            See {@link AsyncTask#cancel(boolean)}
	 * @return The number of tasks cancelled
	 */
	@Nonnegative
	public <C> int cancelTask(@Nonnull Class<? extends C> taskClass, boolean mayInterruptIfRunning) {
		int cancelledTasks = 0;
		for (ParallelAsyncTask<?, ?, ?> task : mManagedTasks) {
			if (task.getClass().equals(taskClass)) {
				task.cancel(mayInterruptIfRunning);
				cancelledTasks++;
			}
		}
		return cancelledTasks;
	}

	/**
	 * Cancels and removes all async tasks previously added to the manager.
	 * 
	 * @param mayInterruptIfRunning
	 *            See {@link AsyncTask#cancel(boolean)}
	 */
	public void cancelAllTasks(boolean mayInterruptIfRunning) {
		for (ParallelAsyncTask<?, ?, ?> task : mManagedTasks) {
			if (task != null) {
				task.cancel(mayInterruptIfRunning);
			}
		}
		mManagedTasks.clear();
	}

}