import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.Value
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion
import io.kloudformation.model.iam.*
import io.kloudformation.property.aws.certificatemanager.certificate.DomainValidationOption
import io.kloudformation.property.aws.ec2.securitygroup.Ingress
import io.kloudformation.property.aws.ecs.service.loadBalancer
import io.kloudformation.property.aws.ecs.taskdefinition.ContainerDefinition
import io.kloudformation.property.aws.ecs.taskdefinition.LogConfiguration
import io.kloudformation.property.aws.ecs.taskdefinition.PortMapping
import io.kloudformation.property.aws.elasticloadbalancingv2.listener.Action
import io.kloudformation.property.aws.elasticloadbalancingv2.listenercertificate.Certificate
import io.kloudformation.property.aws.elasticloadbalancingv2.listenerrule.RuleCondition
import io.kloudformation.property.aws.elasticloadbalancingv2.loadbalancer.LoadBalancerAttribute
import io.kloudformation.property.aws.iam.role.Policy
import io.kloudformation.resource.aws.certificatemanager.certificate
import io.kloudformation.resource.aws.ec2.securityGroup
import io.kloudformation.resource.aws.ecs.cluster
import io.kloudformation.resource.aws.ecs.service
import io.kloudformation.resource.aws.ecs.taskDefinition
import io.kloudformation.resource.aws.elasticloadbalancingv2.listener
import io.kloudformation.resource.aws.elasticloadbalancingv2.listenerRule
import io.kloudformation.resource.aws.elasticloadbalancingv2.loadBalancer
import io.kloudformation.resource.aws.elasticloadbalancingv2.targetGroup
import io.kloudformation.resource.aws.iam.role
import io.kloudformation.resource.aws.logs.logGroup

typealias RuleAction = io.kloudformation.property.aws.elasticloadbalancingv2.listenerrule.Action

class Stack: StackBuilder {
    override fun KloudFormation.create() {
        val serviceName = parameter<String>("ServiceName", default = "kotlin-playground-service")
        val vpcId = parameter<String>("VpcId", default = "vpc-35efcd53")
        val subnetA = parameter<String>("SubnetA", default = "subnet-c38de28b")
        val subnetB = parameter<String>("SubnetB", default = "subnet-cfc11895")

        val cluster = cluster()
        val containerSecurityGroup = securityGroup(+"Access to the Fargate containers"){
            vpcId(vpcId.ref())
            securityGroupIngress(listOf(
                    Ingress(cidrIp = +"0.0.0.0/0", ipProtocol = +"-1")
            ))
        }
        val ecsTaskExecutionRole =  role(
                assumeRolePolicyDocument = policyDocument { statement(action = action("sts:AssumeRole")) { principal(PrincipalType.SERVICE,listOf(+"ecs-tasks.amazonaws.com")) } }
        ){
            path("/")
            policies(listOf(
                    Policy(
                            policyName = +"AmazonECSTaskExecutionRolePolicy",
                            policyDocument = policyDocument {
                                statement(
                                        resource = allResources,
                                        action = actions(
                                                "ecr:GetAuthorizationToken",
                                                "ecr:BatchCheckLayerAvailability",
                                                "ecr:GetDownloadUrlForLayer",
                                                "ecr:BatchGetImage",
                                                "logs:CreateLogStream",
                                                "logs:PutLogEvents"
                                        )
                                )
                            }
                    )
            ))
        }

        val loadBalancerGroup = securityGroup(groupDescription = +"Access to load balancer"){
            vpcId(vpcId.ref())
            securityGroupIngress(listOf(
                    Ingress(cidrIp = +"0.0.0.0/0", ipProtocol = +"-1")
            ))
        }
        val loadBalancer = loadBalancer {
            scheme("internet-facing")
            loadBalancerAttributes(listOf(
                    LoadBalancerAttribute(key = +"idle_timeout.timeout_seconds", value = +"30")
            ))
            subnets(listOf(subnetA.ref(), subnetB.ref()))
            securityGroups(listOf(loadBalancerGroup.ref()))
        }

        val logGroupName = serviceName + "-service"
        val logGroup = logGroup { logGroupName(logGroupName) }
        logGroup.ref()
        val task = taskDefinition {
            family(serviceName.ref())
            cpu("1024")
            memory("2048")
            networkMode("awsvpc")
            requiresCompatibilities(listOf(+"FARGATE"))
            executionRoleArn(ecsTaskExecutionRole.Arn())
            containerDefinitions(listOf(
                    ContainerDefinition(
                            name = serviceName.ref(),
                            image = +"hexlabs/kotlin-playground",
                            portMappings = listOf(
                                    PortMapping(containerPort = Value.Of(80))
                            ),
                            logConfiguration = LogConfiguration(
                                    logDriver = +"awslogs",
                                    options = mapOf(
                                           "awslogs-group" to  logGroupName,
                                            "awslogs-region" to awsRegion,
                                            "awslogs-stream-prefix" to serviceName.ref()
                                    )
                            )
                    )
            ))
        }
        val defaultTargetGroup = targetGroup(port = Value.Of(80),protocol = +"HTTP",vpcId = vpcId.ref())
        val targetGroup = targetGroup(port = Value.Of(80),protocol = +"HTTP",vpcId = vpcId.ref()){
            targetType("ip")
            name(serviceName.ref())
            unhealthyThresholdCount(2)
            matcher(+"200-299")
            healthCheckIntervalSeconds(300)
            healthCheckPath("/")
            healthCheckProtocol("HTTP")
            healthCheckTimeoutSeconds(30)
            healthyThresholdCount(2)
        }
        val certificate = certificate(+"www.playground.hexlabs.io"){
            subjectAlternativeNames(listOf(+"playground.hexlabs.io"))
            domainValidationOptions(listOf(DomainValidationOption(
                    domainName = +"playground.hexlabs.io",
                    validationDomain = +"playground.hexlabs.io"
            )))
            validationMethod("DNS")
        }
        val listener = listener(dependsOn = listOf(loadBalancer.logicalName), defaultActions = listOf(Action(
                targetGroupArn = defaultTargetGroup.ref(),
                type = +"forward"
        )),loadBalancerArn = loadBalancer.ref(), port = Value.Of(443), protocol = +"HTTPS") {
            sslPolicy("ELBSecurityPolicy-2016-08")
            certificates(listOf(Certificate(
                    certificateArn = certificate.ref()
            )))
        }
        val loadBalancerRule = listenerRule(
                actions = listOf(RuleAction(
                        targetGroupArn = targetGroup.ref(),
                        type = +"forward"
                )),
                conditions = listOf(RuleCondition(
                        field = +"path-pattern",
                        values = +listOf(+"/*")
                )),
                priority = Value.Of(1),
                listenerArn = listener.ref()
        )
        service(dependsOn = listOf(loadBalancerRule.logicalName),taskDefinition = task.ref()){
            serviceName(serviceName.ref())
            cluster(cluster.ref())
            launchType("FARGATE")
            deploymentConfiguration {
                maximumPercent(200)
                minimumHealthyPercent(70)
            }
            desiredCount(2)
            networkConfiguration {
                awsvpcConfiguration(+listOf(subnetA.ref(), subnetB.ref())) {
                    assignPublicIp("ENABLED")
                    securityGroups(listOf(containerSecurityGroup.ref()))
                }
            }
            loadBalancers(listOf(
                    loadBalancer(Value.Of(80)){
                        containerName(serviceName.ref())
                        targetGroupArn(targetGroup.ref())
                    }
            ))
        }
    }
}