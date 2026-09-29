package org.around.ieuri.common.persistence

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `entityManagerFactory`가 Flyway 뒤에 오도록 엮는 후처리기 검증. 컨텍스트 없이 빈 정의만 본다.
 * - FactoryBean의 `&` 접두 이름을 정의 이름으로 바꾸는지가 핵심이다.
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
