package org.lumina.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.lumina.fields.ReturnInvalidReasonFields.INVALID_APPROVAL_ID
import org.lumina.fields.ReturnInvalidReasonFields.INVALID_JWT
import org.lumina.models.*
import org.lumina.routes.ApprovalAction.APPROVE
import org.lumina.routes.ApprovalAction.REJECT
import org.lumina.routes.ApprovalAction.WITHDRAW
import org.lumina.utils.*

/**
 * 审批路由
 *
 * 功能：
 * - 根据审批 ID 获取审批信息
 * - 获取自己创建的审批信息
 * - 管理员获取自己所有管理的团体的审批信息
 * - 管理员根据团体 ID 获取该团体的审批信息
 * - 审批创建者撤回自己的审批
 * - 管理员对审批进行通过或拒绝操作
 */
fun Route.approvalRoute() {
    authenticate {
        route("/approvals") {
            get("/{approvalId}") {
                val approvalIdString = call.parameters["approvalId"]?.trim() ?: return@get call.respond(
                    HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                )
                val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, INVALID_JWT
                )
                val approvalId = try {
                    approvalIdString.toLong()
                } catch (_: NumberFormatException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                    )
                }
                protectedRoute(
                    weixinOpenId, approvalIdString, SUPERADMIN_ADMIN_SELF_SET, CheckType.APPROVAL_ID, false
                ) {
                    val approvalInfo = transaction {
                        val approvalRow = Approvals.select(Approvals.approvalId eq approvalId).firstOrNull()
                            ?: throw IllegalArgumentException(INVALID_APPROVAL_ID)
                        val approvalType = approvalRow[Approvals.approvalType]
                        when (approvalType) {
                            ApprovalTargetType.TASK_CREATION -> {
                                TODO()
                            }

                            ApprovalTargetType.GROUP_JOIN -> {
                                buildJoinGroupApprovalInfo(approvalId, approvalRow)
                            }

                            ApprovalTargetType.TASK_EXPAND_GROUP -> {
                                TODO()
                            }
                        }
                    }
                    call.respond(approvalInfo)
                }
            }

            get("/self") {
                val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, INVALID_JWT
                )
                val approvalInfoList = transaction {
                    // val userId = weixinOpenId2UserIdOrNull(weixinOpenId) ?: throw IllegalArgumentException(INVALID_JWT)
                    val approvalInfoList = mutableListOf<ApprovalInfo>()

                    val joinGroupApprovalInfoRows = JoinGroupApprovals.selectAll()
                        .where { JoinGroupApprovals.requesterWeixinOpenId eq weixinOpenId }

                    joinGroupApprovalInfoRows.forEach { joinGroupApprovalInfoRow ->
                        approvalInfoList.add(buildApprovalInfo(joinGroupApprovalInfoRow))
                    }

                    // TODO: taskRequest and taskExpandGroupRequest

                    approvalInfoList.sortedByDescending({ it.createdAt })
                }
                call.respond(
                    Json.encodeToString<List<ApprovalInfo>>(approvalInfoList)
                )
            }

            route("/admin") {
                get {
                    val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@get call.respond(
                        HttpStatusCode.Unauthorized, INVALID_JWT
                    )
                    val approvalInfoList = transaction {
                        val userId =
                            weixinOpenId2UserIdOrNull(weixinOpenId) ?: throw IllegalArgumentException(INVALID_JWT)
                        val managingGroupIdList = UserGroups.selectAll()
                            .where { (UserGroups.userId eq userId) and ((UserGroups.permission eq UserRole.SUPER_ADMIN) or (UserGroups.permission eq UserRole.ADMIN)) }
                            .map { it[UserGroups.groupId] }
                        val approvalInfoList = mutableListOf<ApprovalInfo>()
                        managingGroupIdList.forEach { managingGroupId ->
                            val joinGroupApprovalInfoRows = JoinGroupApprovals.selectAll()
                                .where { JoinGroupApprovals.targetGroupId eq managingGroupId }
                            joinGroupApprovalInfoRows.forEach { joinGroupApprovalInfoRow ->
                                approvalInfoList.add(buildApprovalInfo(joinGroupApprovalInfoRow))
                            }
                        }
                        approvalInfoList.sortedByDescending({ it.createdAt })
                    }
                    call.respond(
                        Json.encodeToString<List<ApprovalInfo>>(approvalInfoList)
                    )
                }
                get("/{groupId}") {
                    val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@get call.respond(
                        HttpStatusCode.Unauthorized, INVALID_JWT
                    )
                    val groupId = call.parameters["groupId"]?.trim() ?: return@get call.respond(
                        HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                    )
                    protectedRoute(
                        weixinOpenId, groupId, SUPERADMIN_ADMIN_SET, CheckType.GROUP_ID, false
                    ) {
                        val approvalInfoList = transaction {
                            val approvalInfoList = mutableListOf<ApprovalInfo>()
                            val joinGroupApprovalInfoRows =
                                JoinGroupApprovals.selectAll().where { JoinGroupApprovals.targetGroupId eq groupId }
                            joinGroupApprovalInfoRows.forEach { joinGroupApprovalInfoRow ->
                                approvalInfoList.add(buildApprovalInfo(joinGroupApprovalInfoRow))
                            }
                            approvalInfoList.sortedByDescending({ it.createdAt })
                        }
                        call.respond(
                            Json.encodeToString<List<ApprovalInfo>>(approvalInfoList)
                        )
                    }
                }
            }

            post("/{approvalId}/withdraw") {
                val approvalIdString = call.parameters["approvalId"]?.trim() ?: return@post call.respond(
                    HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                )
                val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, INVALID_JWT
                )
                val approvalId = try {
                    approvalIdString.toLong()
                } catch (_: NumberFormatException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                    )
                }
                val actionRequest = call.receive<ApprovalActionRequest>().normalized() as ApprovalActionRequest
                val action = requireNotNull(actionRequest.action) { "操作不能为空" }
                if (action != WITHDRAW) throw IllegalArgumentException("用户端操作出错")

                protectedRoute(
                    weixinOpenId,
                    approvalIdString,
                    setOf(RuntimePermission.SELF),
                    CheckType.APPROVAL_ID,
                    soter = true,
                    soterResultFromUser = actionRequest.soterInfo
                ) {
                    transaction {
                        val approvalRow = Approvals.select(Approvals.approvalId eq approvalId).firstOrNull()
                            ?: throw IllegalArgumentException(INVALID_APPROVAL_ID)
                        val approvalType = approvalRow[Approvals.approvalType]
                        if (actionRequest.approvalType != approvalType.toString()) throw IllegalArgumentException("用户端传递的审批类型与实际审批类型不匹配")
                        Approvals.update({ Approvals.approvalId eq approvalId }) {
                            it[status] = ApprovalStatus.WITHDRAWN
                        }
                    }
                    call.respond("撤回成功")
                }
            }

            post("/{approvalId}") {
                val approvalIdString = call.parameters["approvalId"]?.trim() ?: return@post call.respond(
                    HttpStatusCode.BadRequest, INVALID_APPROVAL_ID
                )
                val weixinOpenId = call.principal<JWTPrincipal>()?.get("weixinOpenId")?.trim() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, INVALID_JWT
                )
                val approvalId = try {
                    approvalIdString.toLong()
                } catch (_: NumberFormatException) {
                    return@post call.respond(HttpStatusCode.BadRequest, INVALID_APPROVAL_ID)
                }
                val actionRequest = call.receive<ApprovalActionRequest>().normalized() as ApprovalActionRequest

                protectedRoute(
                    weixinOpenId,
                    approvalIdString,
                    SUPERADMIN_ADMIN_SET,
                    CheckType.APPROVAL_ID,
                    true,
                    actionRequest.soterInfo
                ) {
                    transaction {
                        val approvalRow = Approvals.select(Approvals.approvalId eq approvalId).firstOrNull()
                            ?: throw IllegalArgumentException(INVALID_APPROVAL_ID)
                        val approvalType = approvalRow[Approvals.approvalType]
                        if (actionRequest.approvalType != approvalType.toString()) throw IllegalArgumentException("用户端传递的审批类型与实际审批类型不匹配")
                        when (approvalType) {
                            ApprovalTargetType.TASK_CREATION -> {
                                TODO()
                            }

                            ApprovalTargetType.GROUP_JOIN -> {
                                val joinGroupApprovalRow =
                                    JoinGroupApprovals.select(JoinGroupApprovals.approvalId eq approvalId).firstOrNull()
                                        ?: throw IllegalStateException("服务器错误")
                                when (actionRequest.action) {
                                    APPROVE -> {
                                        val targetGroupId = joinGroupApprovalRow[JoinGroupApprovals.targetGroupId]
                                        val requesterUserId = joinGroupApprovalRow[JoinGroupApprovals.requesterUserId]
                                        val requesterUserName =
                                            joinGroupApprovalRow[JoinGroupApprovals.requesterUserName]
                                        val requesterWeixinOpenId =
                                            joinGroupApprovalRow[JoinGroupApprovals.requesterWeixinOpenId]
                                        Users.insert {
                                            it[userId] = requesterUserId
                                            it[Users.weixinOpenId] = requesterWeixinOpenId
                                            it[userName] = requesterUserName
                                        }
                                        UserGroups.insert {
                                            it[userId] = requesterUserId
                                            it[groupId] = targetGroupId
                                            it[permission] = UserRole.MEMBER
                                        }
                                    }

                                    REJECT -> {
                                        Approvals.update({ Approvals.approvalId eq approvalId }) {
                                            it[status] = ApprovalStatus.REJECTED
                                        }
                                    }
                                }
                            }

                            ApprovalTargetType.TASK_EXPAND_GROUP -> {
                                TODO()
                            }
                        }
                    }

                }
            }
        }
    }
}

@Serializable
data class ApprovalActionRequest(
    val approvalType: String? = null, val action: String? = null, val soterInfo: SoterResultFromUser? = null
)

/**
 * 审批操作
 * @property APPROVE 审批通过
 * @property REJECT 审批拒绝
 * @property WITHDRAW 撤回审批
 */
object ApprovalAction {
    const val APPROVE = "approve"
    const val REJECT = "reject"
    const val WITHDRAW = "withdraw"
}

/**
 * 审批信息
 * @param approvalId 审批 ID
 * @param createdAt 创建时间
 * @param approvalType 审批类型
 * @param status 审批状态
 * @param comment 任务请求者的私下评论
 * @param reviewer 审批人 ID
 * @param reviewerName 审批人昵称
 * @param reviewedAt 审批时间
 */
@Serializable
data class ApprovalInfo(
    val approvalId: Long,
    val createdAt: LocalDateTime,
    val approvalType: String,
    val status: String,
    val comment: String?,
    val reviewer: String?,
    val reviewerName: String?,
    val reviewedAt: LocalDateTime?
)

/**
 * 加入群组审批信息
 * @param approvalId 审批 ID
 * @param targetGroupId 申请加入的群组 ID
 * @param requesterUserId 申请加入的群组的请求者自填写的个人 ID
 * @param requesterUserName 申请加入的群组的请求者自填写的个人昵称
 * @param requesterWeixinOpenId 申请加入的群组的请求者的微信小程序 OpenID，由微信小程序端 wx.login() 自动生成
 * @param createdAt 创建时间
 * @param approvalType 审批类型
 * @param status 审批状态
 * @param comment 任务请求者的私下评论
 * @param reviewer 审批人 ID
 * @param reviewerName 审批人昵称
 * @param reviewedAt 审批时间
 */
@Serializable
data class JoinGroupApprovalInfo(
    val approvalId: Long,
    val targetGroupId: String,
    val requesterUserId: String,
    val requesterUserName: String,
    val requesterWeixinOpenId: String,
    val createdAt: LocalDateTime,
    val approvalType: String,
    val status: String,
    val comment: String?,
    val reviewer: String?,
    val reviewerName: String?,
    val reviewedAt: LocalDateTime?
)

/**
 * 生成审批信息对象
 *
 * @param approvalRow 来自 JetBrains Exposed 查库结果的 ResultRow
 * @return ApprovalInfo 对象
 */
fun Transaction.buildApprovalInfo(approvalRow: ResultRow): ApprovalInfo {
    val reviewer = approvalRow[Approvals.reviewer]
    val reviewerName =
        if (reviewer == null) null else Users.select(Users.userId eq reviewer).firstOrNull()?.get(Users.userName)
    return ApprovalInfo(
        approvalId = approvalRow[Approvals.approvalId],
        createdAt = approvalRow[Approvals.createdAt].toKotlinLocalDateTime(),
        approvalType = approvalRow[Approvals.approvalType].toString(),
        status = approvalRow[Approvals.status].toString(),
        comment = approvalRow[Approvals.comment],
        reviewer = reviewer,
        reviewerName = reviewerName,
        reviewedAt = approvalRow[Approvals.reviewedAt]?.toKotlinLocalDateTime()
    )
}

/**
 * 生成加入群组审批信息对象
 *
 * @param approvalId 审批 ID
 * @param approvalRow 来自 JetBrains Exposed 查库结果的 ResultRow
 * @return JoinGroupApprovalInfo 对象
 */
fun Transaction.buildJoinGroupApprovalInfo(approvalId: Long, approvalRow: ResultRow): JoinGroupApprovalInfo {
    val joinGroupApprovalRow = JoinGroupApprovals.select(JoinGroupApprovals.approvalId eq approvalId).firstOrNull()
        ?: throw IllegalStateException("服务器错误")
    val reviewer = approvalRow[Approvals.reviewer]
    val reviewerName =
        if (reviewer == null) null else Users.select(Users.userId eq reviewer).firstOrNull()?.get(Users.userName)
    return JoinGroupApprovalInfo(
        approvalId = approvalId,
        targetGroupId = joinGroupApprovalRow[JoinGroupApprovals.targetGroupId],
        requesterUserId = joinGroupApprovalRow[JoinGroupApprovals.requesterUserId],
        requesterUserName = joinGroupApprovalRow[JoinGroupApprovals.requesterUserName],
        requesterWeixinOpenId = joinGroupApprovalRow[JoinGroupApprovals.requesterWeixinOpenId],
        createdAt = approvalRow[Approvals.createdAt].toKotlinLocalDateTime(),
        approvalType = approvalRow[Approvals.approvalType].toString(),
        status = approvalRow[Approvals.status].toString(),
        comment = approvalRow[Approvals.comment],
        reviewer = reviewer,
        reviewerName = reviewerName,
        reviewedAt = approvalRow[Approvals.reviewedAt]?.toKotlinLocalDateTime()
    )
}

