window.config = {
  routerBasename: null,
  extensions: [],
  modes: [],
  showStudyList: true,
  dataSources: [
    {
      namespace: '@ohif/extension-default.dataSourcesModule.dicomweb',
      sourceName: 'dicomweb',
      configuration: {
        friendlyName: 'OTCMS local Orthanc',
        name: 'OTCMS Orthanc',
        qidoRoot: '/dicom-web',
        wadoRoot: '/dicom-web',
        wadoUriRoot: '/dicom-web',
        qidoSupportsIncludeField: true,
        imageRendering: 'wadors',
        thumbnailRendering: 'wadors',
        enableStudyLazyLoad: true,
      },
    },
  ],
  defaultDataSourceName: 'dicomweb',
};
