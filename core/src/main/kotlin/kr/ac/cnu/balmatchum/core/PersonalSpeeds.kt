package kr.ac.cnu.balmatchum.core

/**
 * 사용자가 미리 잰 자기 속도(m/s). 상태 판단의 기준이다.
 * 처음 한 번 속도 측정 화면에서 재고, 재기 전에는 [DEFAULT]를 쓴다.
 */
data class PersonalSpeeds(
    /** 보통 걸음. */
    val walkMps: Double,
    /** 빠른 걸음(서두를 때). */
    val briskMps: Double,
    /** 뛰기. */
    val runMps: Double,
) {
    init {
        require(walkMps > 0 && briskMps > 0 && runMps > 0) { "속도는 0보다 커야 한다" }
    }

    /** 세 값이 보통 < 빠른 걸음 < 뛰기 순서인지. 측정이 잘못됐는지 화면에서 알려 줄 때 쓴다. */
    val isOrdered: Boolean get() = walkMps < briskMps && briskMps < runMps

    companion object {
        /** 측정 전 기본값(시작값). 성인 평균 걷기 약 1.3m/s를 기준으로 잡았다. */
        val DEFAULT = PersonalSpeeds(walkMps = 1.3, briskMps = 1.7, runMps = 2.5)
    }
}
