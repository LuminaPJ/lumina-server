package org.lumina.models.task

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.lumina.models.Users
import kotlin.math.log

/**
 * 投票任务信息
 */
object VoteTaskInfoTable: Table("vote_task_info_table") {
    val taskId = reference("task_id", Tasks.taskId, onDelete = ReferenceOption.CASCADE)
    val maxSelectable = integer("max_selectable").default(1).check { it.greater(0)}
    val canRecall = bool("can_recall").default(false) // 是否允许用户撤回选票
    val isResultPublic = bool("is_result_public").default(true) // 结果是否公开
}

/**
 * 投票选项信息
 */
object VoteTaskOptionTable: Table("vote_task_option_table") {
    val optionId = long("option_id").autoIncrement().uniqueIndex()
    val taskId = reference("task_id", Tasks.taskId, onDelete = ReferenceOption.CASCADE)
    val optionName = text("option_name")
    val optionDescription = text("option_description").nullable()
    val sortOrder = integer("sort_order") // 选项序号，用于用户参与页排序选项
}

/**
 * 投票任务参与记录
 */
object VoteTaskParticipationRecord : Table("vote_task_participation_record") {
    val taskId = reference("task_id", Tasks.taskId, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.userId, onDelete = ReferenceOption.CASCADE)
    val selectedOption = reference("selected_option", VoteTaskOptionTable.optionId)
    override val primaryKey = PrimaryKey(taskId, userId, selectedOption)
}


