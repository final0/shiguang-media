package org.example.springaidemo.growth;

/** 必须在既有家庭业务事务、家庭行锁内调用；先取消投递，再变更/删除提醒。 */
final class GrowthReminderLifecycle {
    private GrowthReminderLifecycle() { }
    static void suspend(GrowthStore db, String where, Object... args) {
        db.update("UPDATE reminder_delivery SET status='CANCELLED',result_code='SUSPENDED' WHERE status IN ('QUEUED','SENDING') AND reminder_id IN (SELECT id FROM growth_reminder WHERE "+where+")",args);
        db.update("UPDATE growth_reminder SET state='SUSPENDED',revision=revision+1 WHERE state='ACTIVE' AND ("+where+")",args);
    }
    static void purge(GrowthStore db, String where, Object... args) {
        db.update("DELETE FROM reminder_delivery WHERE reminder_id IN (SELECT id FROM growth_reminder WHERE "+where+")",args);
        db.update("DELETE FROM growth_reminder WHERE "+where,args);
    }
}
