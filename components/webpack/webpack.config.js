const path = require('path')
const webpack = require('webpack')
const createStyledComponentsTransformer = require('typescript-plugin-styled-components').default

module.exports = (env, argv) => {
  const isDevelopment = argv.mode === 'development'
  const styledComponentsTransformer = createStyledComponentsTransformer({
    displayName: isDevelopment
  })
  return {
    devtool: isDevelopment ? '#inline-source-map' : false,
    entry: {
      brave_rewards: path.join(__dirname, '../brave_rewards_ui/resources/brave_rewards'),
    },
    output: {
      path: process.env.TARGET_GEN_DIR,
      filename: '[name].js',
      chunkFilename: '[id].chunk.js'
    },
    plugins: [
    ],
    resolve: {
      extensions: ['.js', '.tsx', '.ts', '.json'],
    },
    module: {
      rules: [
        {
          test: /\.tsx?$/,
          use: [
            {
              loader: 'awesome-typescript-loader',
              options: {
                getCustomTransformers: () => ({ before: [styledComponentsTransformer] })
              }
            }
          ]
        },
        {
          test: /\.less$/,
          loader: 'style-loader!css-loader?-minimize!less-loader'
        },
        {
          test: /\.css$/,
          loader: 'style-loader!css-loader?-minimize'
        },
        {
          test: /\.(ttf|eot|svg|png|jpg)(\?v=[0-9]\.[0-9]\.[0-9])?$/,
          loader: 'file-loader',
          options: {

            name: '[name].[ext]'
          }
        }]
    },
    node: {
      fs: 'empty'
    }
  }
}
