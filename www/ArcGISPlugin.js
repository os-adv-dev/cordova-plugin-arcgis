var exec = require('cordova/exec');

exports.openMaps = function (success, error) {
    exec(success, error, 'ArcGISPlugin', 'openMaps');
};