package org.around.ieuri.common.persistence

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `entityManagerFactory`가 Flyway 뒤에 오도록 엮는 후처리기 검증.
 *
 * Boot의 Flyway 자동구성이 `searchFlyway` 때문에 물러나 순서를 직접 잡아야 하는데, 이 순서가
 * 없으면 `ddl-auto: validate`가 마이그레이션 **전** 스키마를 검증해 기동이 깨진다.
 * 컨텍스트를 띄우지 않고 빈 정의만 본다 — 프레임워크가 아니라 우리가 심는 의존을 검증한다.
 *
 * `getBeanNamesForType`이 FactoryBean에 대해 `&`가 붙은 이름을 준다는 것이 이 테스트가 잡은
 * 실제 버그다.
 */
class DependsOnProbeTest {
    @Test
    fun `entityManagerFactory 정의에 flyway 의존이 추가된다`() {
        val bf = DefaultListableBeanFactory()
        bf.registerBeanDefinition(
            "entityManagerFactory",
            RootBeanDefinition(LocalContainerEntityManagerFactoryBean::class.java),
        )
        val bfpp: BeanFactoryPostProcessor = DataSourceConfig.entityManagerFactoryDependsOnFlyway()
        bfpp.postProcessBeanFactory(bf)

        val dependsOn = bf.getBeanDefinition("entityManagerFactory").dependsOn
        assertTrue(dependsOn != null && "flyway" in dependsOn)
        assertTrue(
            AbstractEntityManagerFactoryBean::class.java
                .isAssignableFrom(LocalContainerEntityManagerFactoryBean::class.java),
        )
    }
}
