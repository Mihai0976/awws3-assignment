package ax.ha.clouddevelopment;


import software.amazon.awscdk.*;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.BucketWebsiteTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.BucketDeploymentProps;
import software.amazon.awscdk.services.s3.deployment.ISource;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class WebsiteBucketStack extends Stack {

    /**
     * Creates a CloudFormation stack for a simple S3 bucket used as a website
     */
    public WebsiteBucketStack(final Construct scope,
                              final String id,
                              final StackProps props,
                              final String groupName) {
        super(scope, id, props);

        // Define your resources here.
        Bucket websiteBucket = Bucket.Builder.create(this, "WebsiteBucket")
                .bucketName(groupName + ".cloud-ha.com")
                .websiteIndexDocument("index.html")
                .build();

        //define static bucket
        Bucket staticContentBucket = Bucket.Builder.create(this, "StaticContentBucket")
                .bucketName(groupName + "-static-content")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        /*
        //Create a CloudFront distribution and connect it to the S3 bucket:
        CloudFrontWebDistributionProps.Builder distributionPropsBuilder = CloudFrontWebDistributionProps.builder()
                .originConfigs(Collections.singletonList(
                        SourceConfiguration.builder()
                                .s3OriginSource(S3OriginConfig.builder()
                                        .s3BucketSource(staticContentBucket)
                                        .build())
                                .behaviors(Collections.singletonList(
                                        Behavior.builder()
                                                .isDefaultBehavior(true)
                                                .build()))
                                .build()))
                .priceClass(PriceClass.PRICE_CLASS_100)
                .geoRestriction(GeoRestriction.allowlist(GeoRestrictionType.BLACKLIST)
                        .locations(Collections.singletonList("FR")) // Block traffic from France
                        .build());

        CloudFrontWebDistribution distribution = new CloudFrontWebDistribution(this, "StaticContentDistribution", distributionPropsBuilder.build());

        new CfnOutput(this, "CloudFrontDistributionDomainName", CfnOutputProps.builder()
                .value(distribution.getDistributionDomainName())
                .build());
        */

        // Define the source directory containing your website files
        ISource source = Source.asset("src/main/resources/website");

        // Define the destination bucket for the deployment
        BucketDeploymentProps.Builder deploymentPropsBuilder = BucketDeploymentProps.builder()
                .destinationBucket(websiteBucket)
                .sources(List.of(source));

        // Create the BucketDeployment construct and add it to the stack
        new BucketDeployment(this, "WebsiteDeployment", deploymentPropsBuilder.build());

        // Define HostedZone
        IHostedZone hostedZone = HostedZone.fromHostedZoneAttributes(this, "MyHostedZone",
                HostedZoneAttributes.builder()
                        .hostedZoneId("Z0413857YT73A0A8FRFF")
                        .zoneName("cloud-ha.com")
                        .build());

        // Define RecordTarget
        RecordTarget recordTarget = RecordTarget.fromAlias(new BucketWebsiteTarget(websiteBucket));

        // Create Route53 RecordSet
        RecordSet recordSet = RecordSet.Builder.create(this, "MyRecordSet")
                .recordType(RecordType.A)
                .target(recordTarget)
                .zone(hostedZone)
                .recordName("mihai-sorinescu.cloud-ha.com")
                .build();

        // Apply removal policy
        recordSet.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Define your IP address and CIDR block
        String dinIpAddress = "185.36.150.245";
        String myCidrBlock = dinIpAddress + "/32";

        // Create a bucket policy statement to allow access only from your IP address
        PolicyStatement bucketPolicyStatement = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .actions(Collections.singletonList("s3:GetObject"))
                .resources(Collections.singletonList(websiteBucket.getBucketArn() + "/*"))
                .principals(Collections.singletonList(new AnyPrincipal()))
                .conditions(Map.of(
                        "IpAddress", Map.of(
                                "aws:SourceIp", Collections.singletonList(myCidrBlock)
                        )
                ))
                .build());

        // Add the bucket policy statement to the bucket
        websiteBucket.addToResourcePolicy(bucketPolicyStatement);
    }
}
