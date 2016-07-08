#import "CameraParameter.h"

@implementation CameraParameter
{
}

@synthesize bgImageData;
@synthesize bgImageData1;
@synthesize bMiniature;
@synthesize bSaveInGallery;
@synthesize nCameraFlashMode;
@synthesize strCameraBGColor;
@synthesize strCameraPressedBG;
@synthesize fQuality;
@synthesize bOpacity;
@synthesize nDefaultFlash;
@synthesize bSwitchFlash;
@synthesize nDefaultCamera;
@synthesize bSwitchCamera;
@synthesize targetSize;

- (id)initWithCommand:(CDVInvokedUrlCommand *)command {
    if (self = [super init]) {
        NSString *strData = [command argumentAtIndex:0];
        if (strData) {
            bgImageData = [[NSData alloc] initWithBase64EncodedString:strData options:0];
        }
        else {
            bgImageData = nil;
        }

        NSString *strData1 = [command argumentAtIndex:1];
        if (strData1) {
            bgImageData1 = [[NSData alloc] initWithBase64EncodedString:strData1 options:0];
        }
        else {
            bgImageData1 = nil;
        }

        bMiniature = [[command argumentAtIndex:2] boolValue];
        bSaveInGallery = [[command argumentAtIndex:3] boolValue];
        strCameraBGColor = [command argumentAtIndex:4];
        strCameraPressedBG = [command argumentAtIndex:5];

        fQuality = [[command argumentAtIndex:6] intValue];
        bOpacity = [[command argumentAtIndex:7] boolValue];
        nDefaultFlash = [[command argumentAtIndex:8] intValue];
        bSwitchFlash = [[command argumentAtIndex:9] boolValue];
        nDefaultCamera = [[command argumentAtIndex:10] intValue];
        bSwitchCamera = [[command argumentAtIndex:11] boolValue];
        //9zai ----------
        NSNumber* targetWidth = [command argumentAtIndex:12 withDefault:nil];
        NSNumber* targetHeight = [command argumentAtIndex:13 withDefault:nil];

        targetSize = CGSizeMake(0, 0);
        if ((targetWidth != nil) && (targetHeight != nil)) {
            targetSize = CGSizeMake([targetWidth floatValue], [targetHeight floatValue]);
            NSLog(@"init targetSize width: %f height: %f",targetSize.width,targetSize.height);
        }
    }
    return self;
}

@end
