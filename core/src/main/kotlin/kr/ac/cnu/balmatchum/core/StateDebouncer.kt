package kr.ac.cnu.balmatchum.core

/**
 * 새 값이 [holdMillis] 이상 이어질 때만 확정 값을 바꾼다. 경계에서 진동이 연달아 울리지 않게 한다.
 * 첫 값은 기다리지 않고 바로 확정한다.
 */
class StateDebouncer<T : Any>(private val holdMillis: Long) {

    var current: T? = null
        private set

    private var candidate: T? = null
    private var candidateSince = 0L

    /** 확정 값이 바뀌었으면 true. */
    fun update(timeMillis: Long, observed: T): Boolean {
        if (current == null) {
            current = observed
            return true
        }
        if (observed == current) {
            candidate = null
            return false
        }
        if (observed != candidate) {
            candidate = observed
            candidateSince = timeMillis
        }
        if (timeMillis - candidateSince >= holdMillis) {
            current = observed
            candidate = null
            return true
        }
        return false
    }
}
