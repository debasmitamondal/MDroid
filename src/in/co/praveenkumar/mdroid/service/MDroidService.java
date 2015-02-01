package in.co.praveenkumar.mdroid.service;

import in.co.praveenkumar.R;
import in.co.praveenkumar.mdroid.activity.CourseActivity;
import in.co.praveenkumar.mdroid.model.MoodleCourse;
import in.co.praveenkumar.mdroid.model.MoodleDiscussion;
import in.co.praveenkumar.mdroid.model.MoodleForum;
import in.co.praveenkumar.mdroid.model.MoodleSiteInfo;
import in.co.praveenkumar.mdroid.task.CourseContentSyncTask;
import in.co.praveenkumar.mdroid.task.DiscussionSyncTask;
import in.co.praveenkumar.mdroid.task.ForumSyncTask;
import in.co.praveenkumar.mdroid.task.MessageSyncTask;
import in.co.praveenkumar.mdroid.task.PostSyncTask;

import java.util.ArrayList;
import java.util.List;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class MDroidService extends Service {
	final String DEBUG_TAG = "MDroid Services";
	Boolean forceCheck = false;
	protected int startId;
	SharedPreferences settings;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(DEBUG_TAG, "Started service!");
		this.startId = startId;
		settings = PreferenceManager.getDefaultSharedPreferences(this);

		// Check if the service started from NotificationActivity
		Bundle extras = intent.getExtras();
		if (extras != null && extras.getBoolean("forceCheck", false)) {
			forceCheck = true;
			showNotification("Checking for content", "Please wait..",
					"You will be notified once complete", "", false);
		}

		// Check for new contents
		new ContentCheckerBg().execute("");
		
		return Service.START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * Does the actual content checking. Loops through all the available
	 * accounts and checks for new contents in each account.
	 * 
	 * @author praveen
	 * 
	 */
	private class ContentCheckerBg extends AsyncTask<String, Integer, Boolean> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		protected Boolean doInBackground(String... credentials) {
			int contentCount = 0;
			int forumCount = 0;
			int discussionCount = 0;
			int postCount = 0;
			int contactCount = 0;
			int participantCount = 0;
			int messageCount = 0;
			int eventCount = 0;

			// Get list of accounts in app
			List<MoodleSiteInfo> mSites = MoodleSiteInfo
					.listAll(MoodleSiteInfo.class);
			MoodleSiteInfo site;
			if (mSites == null || mSites.size() == 0)
				return false;

			// Loop through all sites for checking contents
			for (int i = 0; i < mSites.size(); i++) {
				site = mSites.get(i);

				// Get list of favourites courses
				List<MoodleCourse> mCourses = MoodleCourse.find(
						MoodleCourse.class, "siteid = ? and is_fav_course = ?",
						site.getId() + "", "1");

				// Contents sync
				if (settings.getBoolean("coursecontents", false))
					contentCount = syncCourseContents(site, mCourses);

				// Forums sync
				if (settings.getBoolean("forums", false))
					forumCount = syncForums(site, mCourses);

				// Discussion sync
				if (settings.getBoolean("forumtopics", false))
					discussionCount = syncDiscussions(site, mCourses);

				// Forum posts (replies) sync
				if (settings.getBoolean("forumposts", false))
					postCount = syncPosts(site, mCourses);

				// Messages sync
				if (settings.getBoolean("messages", false))
					messageCount = syncMessages(site);

				setNotificationWithCounts(site, contentCount, forumCount,
						discussionCount, postCount, contactCount,
						participantCount, messageCount, eventCount);
			}

			return true;
		}

		/**
		 * Sync course contents in a site for given courses.
		 * 
		 * @param site
		 *            MoodleSite
		 * @param mCourses
		 *            MoodleCourses whose contents need to be synced
		 * @return Notification count
		 */
		private int syncCourseContents(MoodleSiteInfo site,
				List<MoodleCourse> mCourses) {
			if (mCourses == null || mCourses.size() == 0)
				return 0;

			CourseContentSyncTask ccst = new CourseContentSyncTask(
					site.getSiteurl(), site.getToken(), site.getId(), true);
			for (int i = 0; i < mCourses.size(); i++)
				ccst.syncCourseContents(mCourses.get(i).getCourseid());

			return ccst.getNotificationcount();
		}

		/**
		 * Sync forums in a site for given courses.
		 * 
		 * @param site
		 *            MoodleSite
		 * @param mCourses
		 *            MoodleCourses whose forums need to be synced
		 * @return Notification count
		 */
		private int syncForums(MoodleSiteInfo site, List<MoodleCourse> mCourses) {
			if (mCourses == null || mCourses.size() == 0)
				return 0;

			ArrayList<String> courseids = new ArrayList<String>();
			ForumSyncTask fst = new ForumSyncTask(site.getSiteurl(),
					site.getToken(), site.getId(), true);
			for (int i = 0; i < mCourses.size(); i++)
				courseids.add(mCourses.get(i).getCourseid() + "");
			fst.syncForums(courseids);

			return fst.getNotificationcount();
		}

		/**
		 * Sync discussions in a site for given courses.
		 * 
		 * @param site
		 *            MoodleSite
		 * @param mCourses
		 *            MoodleCourses whose forums need to be synced
		 * @return Notification count
		 */
		private int syncDiscussions(MoodleSiteInfo site,
				List<MoodleCourse> mCourses) {
			if (mCourses == null || mCourses.size() == 0)
				return 0;

			DiscussionSyncTask dst = new DiscussionSyncTask(site.getSiteurl(),
					site.getToken(), site.getId(), true);
			List<MoodleForum> forums = new ArrayList<MoodleForum>();

			// Get list of discussions to sync
			for (int i = 0; i < mCourses.size(); i++)
				forums.addAll(MoodleForum.find(MoodleForum.class,
						"courseid = ? and siteid = ?", mCourses.get(i)
								.getCourseid() + "", site.getId() + ""));

			// Make an Arraylist of ids for above discussions
			ArrayList<String> forumids = new ArrayList<String>();
			for (int i = 0; i < forums.size(); i++)
				forumids.add(forums.get(i).getForumid() + "");

			dst.syncDiscussions(forumids);
			return dst.getNotificationcount();
		}

		/**
		 * Sync posts in a site for given courses - all forum discussions.
		 * 
		 * @param site
		 *            MoodleSite
		 * @param mCourses
		 *            MoodleCourses whose forums need to be synced
		 * @return Notification count
		 */
		private int syncPosts(MoodleSiteInfo site, List<MoodleCourse> mCourses) {
			if (mCourses == null || mCourses.size() == 0)
				return 0;

			PostSyncTask pst = new PostSyncTask(site.getSiteurl(),
					site.getToken(), site.getId(), true);
			List<MoodleDiscussion> discussions = new ArrayList<MoodleDiscussion>();

			// Get list of discussions to sync
			for (int i = 0; i < mCourses.size(); i++)
				discussions.addAll(MoodleDiscussion.find(
						MoodleDiscussion.class, "courseid = ? and siteid = ?",
						mCourses.get(i).getCourseid() + "", site.getId() + ""));

			// Make an Arraylist of ids for above discussions
			ArrayList<Integer> discussionids = new ArrayList<Integer>();
			for (int i = 0; i < discussions.size(); i++)
				discussionids.add(discussions.get(i).getDiscussionid());

			pst.syncPosts(discussionids);
			return pst.getNotificationcount();
		}

		/**
		 * Sync messages of user
		 * 
		 * @param site
		 *            MoodleSite
		 * @return Notification count
		 */
		private int syncMessages(MoodleSiteInfo site) {
			MessageSyncTask mst = new MessageSyncTask(site.getSiteurl(),
					site.getToken(), site.getId(), true);
			mst.syncMessages(site.getUserid());
			return mst.getNotificationcount();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			Log.d(DEBUG_TAG, "MDroidservice exiting itself.");
			stopSelf(startId);
		}
	}

	/**
	 * Set a notification for the given counts in the given site
	 * 
	 * @param site
	 *            MoodleSite
	 * @param contentCount
	 * @param forumCount
	 * @param discussionCount
	 * @param postCount
	 * @param contactCount
	 * @param participantCount
	 * @param messageCount
	 * @param eventCount
	 */
	private void setNotificationWithCounts(MoodleSiteInfo site,
			int contentCount, int forumCount, int discussionCount,
			int postCount, int contactCount, int participantCount,
			int messageCount, int eventCount) {
		int total = contentCount + forumCount + discussionCount + postCount
				+ contactCount + participantCount + messageCount + eventCount;
		int totalForums = postCount + forumCount + discussionCount;
		int totalOthers = contactCount + eventCount + participantCount;

		final String spaces = "     ";
		String contentTitle = "New updates from Moodle";
		String contentText = "Contents : " + contentCount + spaces
				+ " Messages : " + messageCount;
		String subText = "Forums : " + totalForums + spaces + " Others : "
				+ totalOthers;
		String contentInfo = total + " updates";

		showNotification(contentTitle, contentText, subText, contentInfo, true);

	}

	/**
	 * 
	 * @param contentTitle
	 * @param contentText
	 * @param subText
	 * @param contentInfo
	 * @param autoCancel
	 *            If true, notification is cancels itself on click. Not to be
	 *            confused with Notification persistancy.
	 */
	private void showNotification(String contentTitle, String contentText,
			String subText, String contentInfo, Boolean autoCancel) {
		int requestID = (int) System.currentTimeMillis();
		Intent intent = new Intent(this, CourseActivity.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, requestID,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);

		// Define sound URI
		Uri soundUri = RingtoneManager
				.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

		NotificationCompat.Builder notification = new NotificationCompat.Builder(
				this).setContentTitle(contentTitle).setContentText(contentText)
				.setSmallIcon(R.drawable.ic_actionbar_icon).setSubText(subText)
				.setContentInfo(contentInfo).setContentIntent(pIntent)
				.setAutoCancel(autoCancel).setSound(soundUri);
		Bitmap bm = BitmapFactory.decodeResource(getResources(),
				R.drawable.ic_launcher);
		notification.setLargeIcon(bm);

		NotificationManager notificationManager = getNotificationManager();
		notificationManager.notify(1, notification.build());
	}

	// Building notifications
	private NotificationManager getNotificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

}